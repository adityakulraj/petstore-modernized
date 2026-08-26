package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.config.AppProperties;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.notifications.application.CustomerNotificationStore;
import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import com.mongodb.modernization.petstore.supplier.application.SupplierStore;
import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.List;

@Repository
@Profile("oracle")
class OracleSupplierStore implements SupplierStore {
    private final JpaProductRepository products;
    private final JpaSupplierPurchaseOrderRepository purchaseOrders;
    private final JpaOrderRepository orders;
    private final JpaSupplierInventoryCommandRepository inventoryCommands;
    private final TransactionTemplate transactions;
    private final DatabaseExecutor database;
    private final CustomerNotificationStore notifications;
    private final BigDecimal approvalThreshold;
    private final Clock clock = Clock.systemUTC();

    OracleSupplierStore(JpaProductRepository products, JpaSupplierPurchaseOrderRepository purchaseOrders,
                        JpaOrderRepository orders, JpaSupplierInventoryCommandRepository inventoryCommands,
                        PlatformTransactionManager transactionManager, DatabaseExecutor database,
                        CustomerNotificationStore notifications, AppProperties properties) {
        this.products = products;
        this.purchaseOrders = purchaseOrders;
        this.orders = orders;
        this.inventoryCommands = inventoryCommands;
        this.transactions = new TransactionTemplate(transactionManager);
        this.database = database;
        this.notifications = notifications;
        this.approvalThreshold = properties.admin().approvalThreshold();
    }

    @Override
    public List<Product> inventory() {
        return database.execute("supplier.inventory.all", true, () -> products.findAll().stream()
                .map(ProductJpaEntity::toDomain).sorted(Comparator.comparing(Product::id)).toList());
    }

    @Override
    public Product replaceInventory(String productId, long expectedVersion, int quantity, String idempotencyKey) {
        return database.execute("supplier.inventory.replace", true, () -> transactions.execute(ignored -> {
            var replay = inventoryCommands.findById(idempotencyKey);
            if (replay.isPresent()) return replayProduct(replay.get(), productId, expectedVersion, quantity);
            var entity = products.findByIdForUpdate(productId)
                    .orElseThrow(() -> new NotFoundException("Unknown product " + productId));
            // Re-check after acquiring the product lock so concurrent retries see the winner's command.
            replay = inventoryCommands.findById(idempotencyKey);
            if (replay.isPresent()) return replayProduct(replay.get(), productId, expectedVersion, quantity);
            if (entity.stock != quantity && entity.version != expectedVersion) {
                throw new StoreConflictException("Inventory changed in another request; refresh and retry");
            }
            try {
                if (entity.stock != quantity) {
                    entity.replaceStock(quantity);
                    products.saveAndFlush(entity);
                }
                releaseBackorders();
                var result = products.findById(productId).orElseThrow().toDomain();
                inventoryCommands.saveAndFlush(new SupplierInventoryCommandJpaEntity(idempotencyKey, productId,
                        expectedVersion, quantity, result.stock(), result.version(), Instant.now(clock)));
                return result;
            } catch (ObjectOptimisticLockingFailureException conflict) {
                throw new StoreConflictException("Inventory changed in another request; refresh and retry", conflict);
            }
        }));
    }

    @Override
    public List<Order> backorders() {
        return database.execute("supplier.backorders.all", true, () -> orders
                .findAllByStatusOrderByCreatedAtAsc(Order.BACKORDERED).stream().map(OrderJpaEntity::toDomain).toList());
    }

    private Product replayProduct(SupplierInventoryCommandJpaEntity command, String productId,
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

    private void releaseBackorders() {
        for (var entity : orders.findByStatusForUpdate(Order.BACKORDERED)) {
            var lockedProducts = entity.lines.stream().sorted(java.util.Comparator.comparing(line -> line.productId))
                    .map(line -> products.findByIdForUpdate(line.productId)
                            .orElseThrow(() -> new NotFoundException("Unknown product " + line.productId)))
                    .collect(Collectors.toMap(product -> product.id, Function.identity(), (left, right) -> left,
                            java.util.LinkedHashMap::new));
            var quantities = entity.lines.stream().collect(Collectors.toMap(line -> line.productId, line -> line.quantity));
            if (!lockedProducts.values().stream()
                    .allMatch(product -> product.stock >= quantities.get(product.id))) continue;

            lockedProducts.values().forEach(product -> product.reserveStock(quantities.get(product.id)));
            products.saveAllAndFlush(lockedProducts.values());
            var nextStatus = entity.toDomain().statusAfterInventoryAllocation(approvalThreshold);
            entity.allocateInventory(nextStatus);
            var released = orders.saveAndFlush(entity).toDomain();
            var occurredAt = Instant.now(clock);
            notifications.enqueue(released, CustomerNotification.Type.ORDER_INVENTORY_ALLOCATED, occurredAt);
            notifications.enqueue(released, Order.PENDING.equals(nextStatus)
                    ? CustomerNotification.Type.ORDER_PENDING : CustomerNotification.Type.ORDER_APPROVED,
                    occurredAt.plusMillis(1));
            if (Order.APPROVED.equals(nextStatus)) {
                purchaseOrders.findByOrderId(released.id()).orElseGet(() -> purchaseOrders.saveAndFlush(
                        new SupplierPurchaseOrderJpaEntity(SupplierPurchaseOrder.ready(released))));
            }
        }
    }

    @Override
    public List<SupplierPurchaseOrder> purchaseOrders() {
        return database.execute("supplier.purchase_orders.all", true,
                () -> purchaseOrders.findAllByOrderByCreatedAtDesc().stream()
                        .map(SupplierPurchaseOrderJpaEntity::toDomain).toList());
    }

    @Override
    public SupplierPurchaseOrder ensurePurchaseOrder(Order order) {
        try {
            return database.execute("supplier.purchase_order.ensure", true, () -> transactions.execute(ignored ->
                    purchaseOrders.findByOrderId(order.id()).map(SupplierPurchaseOrderJpaEntity::toDomain)
                            .orElseGet(() -> purchaseOrders.saveAndFlush(new SupplierPurchaseOrderJpaEntity(
                                    SupplierPurchaseOrder.ready(order))).toDomain())));
        } catch (DataIntegrityViolationException race) {
            return database.execute("supplier.purchase_order.replay", true,
                    () -> purchaseOrders.findByOrderId(order.id()).orElseThrow(() -> race).toDomain());
        }
    }

    @Override
    public SupplierPurchaseOrder processPurchaseOrder(String purchaseOrderId, long expectedVersion) {
        try {
            return database.execute("supplier.purchase_order.process", true, () -> transactions.execute(ignored -> {
                var entity = purchaseOrders.findById(purchaseOrderId)
                        .orElseThrow(() -> new NotFoundException("Unknown supplier purchase order " + purchaseOrderId));
                if (entity.status == SupplierPurchaseOrder.Status.PROCESSED) {
                    ensureCompletedNotification(entity);
                    return entity.toDomain();
                }
                if (entity.version != expectedVersion) {
                    throw new StoreConflictException("Purchase order changed in another request; refresh and retry");
                }
                entity.markProcessed(Instant.now(clock));
                var processed = purchaseOrders.saveAndFlush(entity).toDomain();
                if (orders.completeApproved(entity.orderId, Order.COMPLETED) != 1) {
                    throw new StoreConflictException("Customer order was not ready for supplier completion");
                }
                ensureCompletedNotification(entity);
                return processed;
            }));
        } catch (ObjectOptimisticLockingFailureException conflict) {
            return processedReplay(purchaseOrderId, conflict);
        }
    }

    private void ensureCompletedNotification(SupplierPurchaseOrderJpaEntity purchaseOrder) {
        var order = orders.findById(purchaseOrder.orderId)
                .orElseThrow(() -> new NotFoundException("Unknown order " + purchaseOrder.orderId)).toDomain();
        if (!Order.COMPLETED.equals(order.status())) {
            throw new StoreConflictException("Customer order was not completed with the supplier purchase order");
        }
        notifications.enqueue(order, CustomerNotification.Type.ORDER_COMPLETED,
                purchaseOrder.processedAt == null ? Instant.now(clock) : purchaseOrder.processedAt);
    }

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
