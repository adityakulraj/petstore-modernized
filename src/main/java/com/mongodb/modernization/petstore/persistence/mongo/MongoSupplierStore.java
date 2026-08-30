package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.config.AppProperties;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.notifications.application.CustomerNotificationStore;
import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.payments.application.PaymentStore;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import com.mongodb.modernization.petstore.supplier.application.SupplierStore;
import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Repository
@Profile("mongo")
class MongoSupplierStore implements SupplierStore {
    private final MongoProductRepository products;
    private final MongoSupplierPurchaseOrderRepository purchaseOrders;
    private final MongoOrderRepository orders;
    private final MongoSupplierInventoryCommandRepository inventoryCommands;
    private final MongoTemplate template;
    private final TransactionTemplate transactions;
    private final DatabaseExecutor database;
    private final CustomerNotificationStore notifications;
    private final PaymentStore payments;
    private final BigDecimal approvalThreshold;
    private final Clock clock = Clock.systemUTC();

    /** Creates the MongoDB supplier adapter and its transaction template. */
    MongoSupplierStore(MongoProductRepository products, MongoSupplierPurchaseOrderRepository purchaseOrders,
                       MongoOrderRepository orders, MongoSupplierInventoryCommandRepository inventoryCommands,
                       MongoTemplate template,
                       @Qualifier("mongoTransactionManager") PlatformTransactionManager transactionManager,
                       DatabaseExecutor database, CustomerNotificationStore notifications, AppProperties properties,
                       PaymentStore payments) {
        this.products = products;
        this.purchaseOrders = purchaseOrders;
        this.orders = orders;
        this.inventoryCommands = inventoryCommands;
        this.template = template;
        this.transactions = new TransactionTemplate(transactionManager);
        this.database = database;
        this.notifications = notifications;
        this.payments = payments;
        this.approvalThreshold = properties.admin().approvalThreshold();
    }

    @Override
    /** Returns all independently stocked item variants in stable SKU order. */
    public List<Product> inventory() {
        return database.execute("supplier.inventory.all", true, () -> products.findAll().stream()
                .map(ProductDocument::toDomain).sorted(Comparator.comparing(Product::id)).toList());
    }

    @Override
    /** Replaces absolute stock once per idempotency key and rejects stale product versions. */
    public Product replaceInventory(String productId, long expectedVersion, int quantity, String idempotencyKey) {
        try {
            return database.execute("supplier.inventory.replace", true, () -> transactions.execute(ignored -> {
            var replay = inventoryCommands.findById(idempotencyKey);
            if (replay.isPresent()) return replayProduct(replay.get(), productId, expectedVersion, quantity);
            var current = products.findById(productId)
                    .orElseThrow(() -> new NotFoundException("Unknown product " + productId));
            if (current.stock != quantity && (current.version == null ? 0 : current.version) != expectedVersion) {
                throw new StoreConflictException("Inventory changed in another request; refresh and retry");
            }
            if (current.stock != quantity) {
                var query = Query.query(Criteria.where("_id").is(productId).and("version").is(current.version));
                var updated = template.updateFirst(query, new Update().set("stock", quantity).inc("version", 1),
                        ProductDocument.class);
                if (updated.getModifiedCount() != 1) {
                    throw new StoreConflictException("Inventory changed in another request; refresh and retry");
                }
            }
            releaseBackorders();
            var result = products.findById(productId).orElseThrow().toDomain();
            inventoryCommands.insert(new SupplierInventoryCommandDocument(idempotencyKey, productId,
                    expectedVersion, quantity, result.stock(), result.version(), Instant.now(clock)));
            return result;
            }));
        } catch (DataIntegrityViolationException race) {
            return database.execute("supplier.inventory.replay", true, () -> inventoryCommands.findById(idempotencyKey)
                    .map(command -> replayProduct(command, productId, expectedVersion, quantity))
                    .orElseThrow(() -> race));
        }
    }

    @Override
    /** Returns backordered customer orders oldest first for deterministic replenishment. */
    public List<Order> backorders() {
        return database.execute("supplier.backorders.all", true, () -> orders
                .findByStatusOrderByCreatedAtAsc(Order.BACKORDERED).stream().map(OrderDocument::toDomain).toList());
    }

    /** Validates and returns the prior result of an identical inventory command. */
    private Product replayProduct(SupplierInventoryCommandDocument command, String productId,
                                  long expectedVersion, int quantity) {
        if (!command.matches(productId, expectedVersion, quantity)) {
            throw new StoreConflictException("Idempotency key was already used for a different inventory command");
        }
        var current = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("Unknown product " + productId)).toDomain();
        return new Product(current.id(), current.productGroupId(), current.variantName(), current.categoryId(),
                current.categoryName(), current.name(), current.description(), current.price(),
                command.resultStock, command.resultVersion);
    }

    /** Allocates replenished stock oldest-first and advances only fully satisfiable backorders. */
    private void releaseBackorders() {
        for (var document : orders.findByStatusOrderByCreatedAtAsc(Order.BACKORDERED)) {
            if (!inventoryAvailable(document)) continue;
            for (var line : document.lines) {
                var reserved = template.updateFirst(Query.query(Criteria.where("_id").is(line.productId)
                                .and("stock").gte(line.quantity)),
                        new Update().inc("stock", -line.quantity).inc("version", 1), ProductDocument.class);
                if (reserved.getModifiedCount() != 1) {
                    throw new OptimisticLockingFailureException("Inventory changed while allocating a backorder");
                }
            }
            var order = document.toDomain();
            var nextStatus = order.statusAfterInventoryAllocation(approvalThreshold);
            var advanced = template.updateFirst(Query.query(Criteria.where("_id").is(document.id)
                            .and("status").is(Order.BACKORDERED).and("version").is(document.version)),
                    new Update().set("status", nextStatus).inc("version", 1), OrderDocument.class);
            if (advanced.getModifiedCount() != 1) {
                throw new OptimisticLockingFailureException("Backorder changed while allocating inventory");
            }
            var released = orders.findById(document.id).orElseThrow().toDomain();
            var occurredAt = Instant.now(clock);
            notifications.enqueue(released, CustomerNotification.Type.ORDER_INVENTORY_ALLOCATED, occurredAt);
            notifications.enqueue(released, Order.PENDING.equals(nextStatus)
                    ? CustomerNotification.Type.ORDER_PENDING : CustomerNotification.Type.ORDER_APPROVED,
                    occurredAt.plusMillis(1));
            if (Order.APPROVED.equals(nextStatus)) {
                purchaseOrders.findByOrderId(released.id()).orElseGet(() -> purchaseOrders.insert(
                        new SupplierPurchaseOrderDocument(SupplierPurchaseOrder.ready(released))));
            }
        }
    }

    /** Checks whether every order line currently has enough stock for atomic allocation. */
    private boolean inventoryAvailable(OrderDocument order) {
        return order.lines.stream().allMatch(line -> products.findById(line.productId)
                .map(product -> product.stock >= line.quantity).orElse(false));
    }

    @Override
    /** Returns supplier purchase orders in reverse creation order. */
    public List<SupplierPurchaseOrder> purchaseOrders() {
        return database.execute("supplier.purchase_orders.all", true,
                () -> purchaseOrders.findAllByOrderByCreatedAtDesc().stream()
                        .map(SupplierPurchaseOrderDocument::toDomain).toList());
    }

    @Override
    /** Idempotently creates one deterministic supplier purchase order per approved customer order. */
    public SupplierPurchaseOrder ensurePurchaseOrder(Order order) {
        try {
            return database.execute("supplier.purchase_order.ensure", true, () -> transactions.execute(ignored ->
                    purchaseOrders.findByOrderId(order.id()).map(SupplierPurchaseOrderDocument::toDomain)
                            .orElseGet(() -> {
                                var current = orders.findById(order.id()).orElseThrow(() ->
                                        new NotFoundException("Unknown order " + order.id())).toDomain();
                                if (!current.supplierReady()) throw new StoreConflictException(
                                        "Order is no longer eligible for supplier fulfilment");
                                return purchaseOrders.insert(new SupplierPurchaseOrderDocument(
                                        SupplierPurchaseOrder.ready(current))).toDomain();
                            })));
        } catch (DataIntegrityViolationException race) {
            return database.execute("supplier.purchase_order.replay", true,
                    () -> purchaseOrders.findByOrderId(order.id()).orElseThrow(() -> race).toDomain());
        }
    }

    @Override
    /** Atomically processes fulfilment, completes the order, captures payment, and emits notifications. */
    public SupplierPurchaseOrder processPurchaseOrder(String purchaseOrderId, long expectedVersion) {
        try {
            return database.execute("supplier.purchase_order.process", true, () -> transactions.execute(ignored -> {
                var document = purchaseOrders.findById(purchaseOrderId)
                        .orElseThrow(() -> new NotFoundException("Unknown supplier purchase order " + purchaseOrderId));
                if (document.status == SupplierPurchaseOrder.Status.PROCESSED) {
                    ensureCompletedPaymentAndNotifications(document);
                    return document.toDomain();
                }
                if (document.status != SupplierPurchaseOrder.Status.READY) {
                    throw new StoreConflictException("Cancelled purchase orders cannot be processed");
                }
                long actualVersion = document.version == null ? 0 : document.version;
                if (actualVersion != expectedVersion) {
                    throw new StoreConflictException("Purchase order changed in another request; refresh and retry");
                }
                document.markProcessed(Instant.now(clock));
                var processed = purchaseOrders.save(document).toDomain();
                var completed = template.updateFirst(Query.query(Criteria.where("_id").is(document.orderId)
                                .and("status").is(Order.APPROVED)),
                        Update.update("status", Order.COMPLETED).inc("version", 1), OrderDocument.class);
                if (completed.getModifiedCount() != 1) {
                    throw new StoreConflictException("Customer order was not ready for supplier completion");
                }
                ensureCompletedPaymentAndNotifications(document);
                return processed;
            }));
        } catch (OptimisticLockingFailureException conflict) {
            return processedReplay(purchaseOrderId, conflict);
        }
    }

    /** Repairs payment capture and terminal notifications when a processed replay follows an interrupted hand-off. */
    private void ensureCompletedPaymentAndNotifications(SupplierPurchaseOrderDocument purchaseOrder) {
        var order = orders.findById(purchaseOrder.orderId)
                .orElseThrow(() -> new NotFoundException("Unknown order " + purchaseOrder.orderId)).toDomain();
        if (!Order.COMPLETED.equals(order.status())) {
            throw new StoreConflictException("Customer order was not completed with the supplier purchase order");
        }
        var occurredAt = purchaseOrder.processedAt == null ? Instant.now(clock) : purchaseOrder.processedAt;
        payments.capture(order, occurredAt);
        notifications.enqueue(order, CustomerNotification.Type.PAYMENT_CAPTURED, occurredAt);
        notifications.enqueue(order, CustomerNotification.Type.ORDER_COMPLETED, occurredAt.plusMillis(1));
    }

    /** Reloads and validates the committed result of a concurrent supplier-processing winner. */
    private SupplierPurchaseOrder processedReplay(String purchaseOrderId, RuntimeException conflict) {
        return database.execute("supplier.purchase_order.replay", true, () -> {
            var purchaseOrder = purchaseOrders.findById(purchaseOrderId)
                    .orElseThrow(() -> new StoreConflictException("Purchase order changed in another request", conflict))
                    .toDomain();
            if (purchaseOrder.status() != SupplierPurchaseOrder.Status.PROCESSED) {
                throw new StoreConflictException("Purchase order changed in another request; refresh and retry", conflict);
            }
            return purchaseOrder;
        });
    }
}
