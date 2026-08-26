package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.domain.Order;
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
import java.util.Comparator;
import java.util.List;

@Repository
@Profile("oracle")
class OracleSupplierStore implements SupplierStore {
    private final JpaProductRepository products;
    private final JpaSupplierPurchaseOrderRepository purchaseOrders;
    private final JpaOrderRepository orders;
    private final TransactionTemplate transactions;
    private final DatabaseExecutor database;
    private final Clock clock = Clock.systemUTC();

    OracleSupplierStore(JpaProductRepository products, JpaSupplierPurchaseOrderRepository purchaseOrders,
                        JpaOrderRepository orders, PlatformTransactionManager transactionManager,
                        DatabaseExecutor database) {
        this.products = products;
        this.purchaseOrders = purchaseOrders;
        this.orders = orders;
        this.transactions = new TransactionTemplate(transactionManager);
        this.database = database;
    }

    @Override
    public List<Product> inventory() {
        return database.execute("supplier.inventory.all", true, () -> products.findAll().stream()
                .map(ProductJpaEntity::toDomain).sorted(Comparator.comparing(Product::id)).toList());
    }

    @Override
    public Product replaceInventory(String productId, long expectedVersion, int quantity) {
        return database.execute("supplier.inventory.replace", true, () -> transactions.execute(ignored -> {
            var entity = products.findById(productId)
                    .orElseThrow(() -> new NotFoundException("Unknown product " + productId));
            if (entity.stock == quantity) return entity.toDomain(); // identical PUT replay
            if (entity.version != expectedVersion) {
                throw new StoreConflictException("Inventory changed in another request; refresh and retry");
            }
            try {
                entity.replaceStock(quantity);
                return products.saveAndFlush(entity).toDomain();
            } catch (ObjectOptimisticLockingFailureException conflict) {
                throw new StoreConflictException("Inventory changed in another request; refresh and retry", conflict);
            }
        }));
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
                if (entity.status == SupplierPurchaseOrder.Status.PROCESSED) return entity.toDomain();
                if (entity.version != expectedVersion) {
                    throw new StoreConflictException("Purchase order changed in another request; refresh and retry");
                }
                entity.markProcessed(Instant.now(clock));
                var processed = purchaseOrders.saveAndFlush(entity).toDomain();
                orders.completeApproved(entity.orderId, Order.COMPLETED);
                return processed;
            }));
        } catch (ObjectOptimisticLockingFailureException conflict) {
            return processedReplay(purchaseOrderId, conflict);
        }
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
