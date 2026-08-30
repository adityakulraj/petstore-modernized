package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.notifications.application.CustomerNotificationStore;
import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.application.CustomerOrderActionStore;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.payments.application.PaymentStore;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

@Repository
@Profile("mongo")
class MongoCustomerOrderActionStore implements CustomerOrderActionStore {
    private enum Action { CANCEL, REFUND }

    private final MongoOrderRepository orders;
    private final MongoCustomerOrderCommandRepository commands;
    private final MongoSupplierPurchaseOrderRepository purchaseOrders;
    private final MongoTemplate template;
    private final TransactionTemplate transactions;
    private final DatabaseExecutor database;
    private final PaymentStore payments;
    private final CustomerNotificationStore notifications;
    private final Clock clock = Clock.systemUTC();

    MongoCustomerOrderActionStore(MongoOrderRepository orders, MongoCustomerOrderCommandRepository commands,
                                  MongoSupplierPurchaseOrderRepository purchaseOrders, MongoTemplate template,
                                  @Qualifier("mongoTransactionManager") PlatformTransactionManager manager,
                                  DatabaseExecutor database, PaymentStore payments,
                                  CustomerNotificationStore notifications) {
        this.orders = orders; this.commands = commands; this.purchaseOrders = purchaseOrders; this.template = template;
        this.transactions = new TransactionTemplate(manager); this.database = database; this.payments = payments;
        this.notifications = notifications;
    }

    @Override public Order cancel(String customerId, String orderId, long version, String key, String reason) {
        return execute(customerId, orderId, version, key, reason.trim(), Action.CANCEL);
    }

    @Override public Order refund(String customerId, String orderId, long version, String key, String reason) {
        return execute(customerId, orderId, version, key, reason.trim(), Action.REFUND);
    }

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

    private Order replayAfterRace(String customerId, String orderId, long version, String key, String reason,
                                  Action action, RuntimeException race) {
        var command = commands.findById(customerId + ":" + key).orElseThrow(() -> race);
        if (!command.matches(orderId, action.name(), version, reason)) {
            throw new StoreConflictException("Idempotency key was already used for a different customer order action");
        }
        return ownedOrder(customerId, orderId);
    }

    private Order executeOnce(String customerId, String orderId, long version, String key, String reason, Action action) {
        var commandId = customerId + ":" + key;
        var replay = commands.findById(commandId);
        if (replay.isPresent()) {
            if (!replay.get().matches(orderId, action.name(), version, reason)) {
                throw new StoreConflictException("Idempotency key was already used for a different customer order action");
            }
            return ownedOrder(customerId, orderId);
        }

        var currentDocument = orders.findById(orderId)
                .filter(value -> customerId.equals(value.customerId))
                .orElseThrow(() -> new NotFoundException("Unknown order " + orderId));
        var current = currentDocument.toDomain();
        var target = action == Action.CANCEL ? Order.CANCELLED : Order.REFUNDED;
        if (target.equals(current.status())) {
            commands.insert(new CustomerOrderCommandDocument(customerId, key, orderId, action.name(), version,
                    reason, current.status(), current.version(), Instant.now(clock)));
            enqueueTerminal(current, action, Instant.now(clock));
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

        var updated = template.updateFirst(Query.query(Criteria.where("_id").is(orderId)
                        .and("customerId").is(customerId).and("status").is(current.status())
                        .and("version").is(currentDocument.version)),
                new Update().set("status", target).inc("version", 1), OrderDocument.class);
        if (updated.getModifiedCount() != 1) {
            throw new StoreConflictException("Order changed in another request; refresh and retry");
        }

        var now = Instant.now(clock);
        if (action == Action.CANCEL) {
            cancelReadyPurchaseOrder(current);
            if (Order.PENDING.equals(current.status()) || Order.APPROVED.equals(current.status())) restoreInventory(current);
            payments.voidAuthorization(current, now);
        } else {
            payments.refund(current, now);
        }
        var result = ownedOrder(customerId, orderId);
        enqueueTerminal(result, action, now);
        commands.insert(new CustomerOrderCommandDocument(customerId, key, orderId, action.name(), version,
                reason, result.status(), result.version(), now));
        return result;
    }

    private void cancelReadyPurchaseOrder(Order order) {
        purchaseOrders.findByOrderId(order.id()).ifPresent(po -> {
            if (po.status == SupplierPurchaseOrder.Status.PROCESSED) {
                throw new StoreConflictException("Supplier already processed this order; request a refund instead");
            }
            if (po.status == SupplierPurchaseOrder.Status.READY) {
                var changed = template.updateFirst(Query.query(Criteria.where("_id").is(po.id)
                                .and("status").is(SupplierPurchaseOrder.Status.READY).and("version").is(po.version)),
                        new Update().set("status", SupplierPurchaseOrder.Status.CANCELLED).inc("version", 1),
                        SupplierPurchaseOrderDocument.class);
                if (changed.getModifiedCount() != 1) throw new StoreConflictException("Supplier order changed; refresh and retry");
            }
        });
    }

    private void restoreInventory(Order order) {
        for (var line : order.lines()) {
            if (template.updateFirst(Query.query(Criteria.where("_id").is(line.productId())),
                    new Update().inc("stock", line.quantity()).inc("version", 1), ProductDocument.class)
                    .getModifiedCount() != 1) throw new NotFoundException("Unknown product " + line.productId());
        }
    }

    private void enqueueTerminal(Order order, Action action, Instant now) {
        notifications.enqueue(order, action == Action.CANCEL
                ? CustomerNotification.Type.PAYMENT_VOIDED : CustomerNotification.Type.PAYMENT_REFUNDED, now);
        notifications.enqueue(order, action == Action.CANCEL
                ? CustomerNotification.Type.ORDER_CANCELLED : CustomerNotification.Type.ORDER_REFUNDED, now.plusMillis(1));
    }

    private Order ownedOrder(String customerId, String orderId) {
        return orders.findById(orderId).filter(value -> customerId.equals(value.customerId))
                .map(OrderDocument::toDomain).orElseThrow(() -> new NotFoundException("Unknown order " + orderId));
    }
}
