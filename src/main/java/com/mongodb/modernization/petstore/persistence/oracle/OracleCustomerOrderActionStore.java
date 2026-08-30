package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.notifications.application.CustomerNotificationStore;
import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.application.CustomerOrderActionStore;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.payments.application.PaymentStore;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

@Repository
@Profile("oracle")
class OracleCustomerOrderActionStore implements CustomerOrderActionStore {
    private enum Action { CANCEL, REFUND }

    private final JpaOrderRepository orders;
    private final JpaCustomerOrderCommandRepository commands;
    private final JpaSupplierPurchaseOrderRepository purchaseOrders;
    private final JpaProductRepository products;
    private final TransactionTemplate transactions;
    private final DatabaseExecutor database;
    private final PaymentStore payments;
    private final CustomerNotificationStore notifications;
    private final Clock clock = Clock.systemUTC();

    /** Creates the Oracle cancellation/refund adapter with JPA transaction dependencies. */
    OracleCustomerOrderActionStore(JpaOrderRepository orders, JpaCustomerOrderCommandRepository commands,
                                   JpaSupplierPurchaseOrderRepository purchaseOrders, JpaProductRepository products,
                                   PlatformTransactionManager manager, DatabaseExecutor database, PaymentStore payments,
                                   CustomerNotificationStore notifications) {
        this.orders = orders; this.commands = commands; this.purchaseOrders = purchaseOrders; this.products = products;
        this.transactions = new TransactionTemplate(manager); this.database = database; this.payments = payments;
        this.notifications = notifications;
    }

    /** Executes an idempotent customer cancellation with version and ownership checks. */
    @Override public Order cancel(String customerId, String orderId, long version, String key, String reason) {
        return execute(customerId, orderId, version, key, reason.trim(), Action.CANCEL);
    }

    /** Executes an idempotent customer refund with version and ownership checks. */
    @Override public Order refund(String customerId, String orderId, long version, String key, String reason) {
        return execute(customerId, orderId, version, key, reason.trim(), Action.REFUND);
    }

    /** Runs the action transaction and resolves a concurrent unique-key winner as an idempotent replay. */
    private Order execute(String customerId, String orderId, long version, String key, String reason, Action action) {
        var operation = action == Action.CANCEL ? "orders.cancel" : "orders.refund";
        try {
            return database.execute(operation, true,
                    () -> transactions.execute(ignored -> executeOnce(customerId, orderId, version, key, reason, action)));
        } catch (DataIntegrityViolationException race) {
            return database.execute(operation + ".replay", true, () -> replayAfterRace(
                    customerId, orderId, version, key, reason, action, race));
        }
    }

    /** Validates that the winning idempotency record represents the exact same customer command. */
    private Order replayAfterRace(String customerId, String orderId, long version, String key, String reason,
                                  Action action, RuntimeException race) {
        var command = commands.findById(customerId + ":" + key).orElseThrow(() -> race);
        if (!command.matches(orderId, action.name(), version, reason)) {
            throw new StoreConflictException("Idempotency key was already used for a different customer order action");
        }
        return ownedOrder(customerId, orderId);
    }

    /** Atomically changes order, payment, inventory, supplier hand-off, notifications, and command record. */
    private Order executeOnce(String customerId, String orderId, long version, String key, String reason, Action action) {
        var commandId = customerId + ":" + key;
        var replay = commands.findById(commandId);
        if (replay.isPresent()) {
            if (!replay.get().matches(orderId, action.name(), version, reason)) {
                throw new StoreConflictException("Idempotency key was already used for a different customer order action");
            }
            return ownedOrder(customerId, orderId);
        }

        var entity = orders.findByIdForReview(orderId)
                .filter(value -> customerId.equals(value.customerId))
                .orElseThrow(() -> new NotFoundException("Unknown order " + orderId));
        var current = entity.toDomain();
        var target = action == Action.CANCEL ? Order.CANCELLED : Order.REFUNDED;
        if (target.equals(current.status())) {
            var now = Instant.now(clock);
            commands.saveAndFlush(new CustomerOrderCommandJpaEntity(customerId, key, orderId, action.name(), version,
                    reason, current.status(), current.version(), now));
            enqueueTerminal(current, action, now);
            return current;
        }
        if (current.version() != version) {
            throw new StoreConflictException("Order changed in another request; refresh and retry");
        }
        if (action == Action.CANCEL && !current.cancellable()) {
            throw new StoreConflictException(Order.COMPLETED.equals(current.status())
                    ? "Completed orders cannot be cancelled; request a refund instead"
                    : "Only backordered, pending, or approved orders can be cancelled");
        }
        if (action == Action.REFUND && !Order.COMPLETED.equals(current.status())) {
            throw new StoreConflictException("Only completed orders with captured payment can be refunded");
        }

        var now = Instant.now(clock);
        if (action == Action.CANCEL) {
            cancelReadyPurchaseOrder(current);
            if (Order.PENDING.equals(current.status()) || Order.APPROVED.equals(current.status())) restoreInventory(current);
            payments.voidAuthorization(current, now);
        } else {
            payments.refund(current, now);
        }
        entity.customerTransition(target);
        var result = orders.saveAndFlush(entity).toDomain();
        enqueueTerminal(result, action, now);
        commands.saveAndFlush(new CustomerOrderCommandJpaEntity(customerId, key, orderId, action.name(), version,
                reason, result.status(), result.version(), now));
        return result;
    }

    /** Conditionally cancels an unprocessed supplier purchase order and rejects a fulfilment race. */
    private void cancelReadyPurchaseOrder(Order order) {
        purchaseOrders.findByOrderIdForUpdate(order.id()).ifPresent(po -> {
            if (po.status == SupplierPurchaseOrder.Status.PROCESSED) {
                throw new StoreConflictException("Supplier already processed this order; request a refund instead");
            }
            if (po.status == SupplierPurchaseOrder.Status.READY) {
                po.cancel(); purchaseOrders.saveAndFlush(po);
            }
        });
    }

    /** Restores every previously reserved order-line quantity within the action transaction. */
    private void restoreInventory(Order order) {
        for (var line : order.lines()) {
            if (products.restoreStock(line.productId(), line.quantity()) != 1) {
                throw new NotFoundException("Unknown product " + line.productId());
            }
        }
    }

    /** Enqueues payment and order terminal events in deterministic timestamp order. */
    private void enqueueTerminal(Order order, Action action, Instant now) {
        notifications.enqueue(order, action == Action.CANCEL
                ? CustomerNotification.Type.PAYMENT_VOIDED : CustomerNotification.Type.PAYMENT_REFUNDED, now);
        notifications.enqueue(order, action == Action.CANCEL
                ? CustomerNotification.Type.ORDER_CANCELLED : CustomerNotification.Type.ORDER_REFUNDED, now.plusMillis(1));
    }

    /** Reloads an order only when it belongs to the authenticated customer. */
    private Order ownedOrder(String customerId, String orderId) {
        return orders.findById(orderId).filter(value -> customerId.equals(value.customerId))
                .map(OrderJpaEntity::toDomain).orElseThrow(() -> new NotFoundException("Unknown order " + orderId));
    }
}
