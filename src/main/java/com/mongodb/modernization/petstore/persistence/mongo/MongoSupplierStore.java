package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.domain.Order;
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
import java.util.Comparator;
import java.util.List;

@Repository
@Profile("mongo")
class MongoSupplierStore implements SupplierStore {
    private final MongoProductRepository products;
    private final MongoSupplierPurchaseOrderRepository purchaseOrders;
    private final MongoTemplate template;
    private final TransactionTemplate transactions;
    private final DatabaseExecutor database;
    private final Clock clock = Clock.systemUTC();

    MongoSupplierStore(MongoProductRepository products, MongoSupplierPurchaseOrderRepository purchaseOrders,
                       MongoTemplate template,
                       @Qualifier("mongoTransactionManager") PlatformTransactionManager transactionManager,
                       DatabaseExecutor database) {
        this.products = products;
        this.purchaseOrders = purchaseOrders;
        this.template = template;
        this.transactions = new TransactionTemplate(transactionManager);
        this.database = database;
    }

    @Override
    public List<Product> inventory() {
        return database.execute("supplier.inventory.all", true, () -> products.findAll().stream()
                .map(ProductDocument::toDomain).sorted(Comparator.comparing(Product::id)).toList());
    }

    @Override
    public Product replaceInventory(String productId, long expectedVersion, int quantity) {
        return database.execute("supplier.inventory.replace", true, () -> transactions.execute(ignored -> {
            var current = products.findById(productId)
                    .orElseThrow(() -> new NotFoundException("Unknown product " + productId));
            if (current.stock == quantity) return current.toDomain(); // identical PUT replay
            if ((current.version == null ? 0 : current.version) != expectedVersion) {
                throw new StoreConflictException("Inventory changed in another request; refresh and retry");
            }
            var query = Query.query(Criteria.where("_id").is(productId).and("version").is(current.version));
            var updated = template.updateFirst(query, new Update().set("stock", quantity).inc("version", 1),
                    ProductDocument.class);
            if (updated.getModifiedCount() != 1) {
                throw new StoreConflictException("Inventory changed in another request; refresh and retry");
            }
            return products.findById(productId).orElseThrow().toDomain();
        }));
    }

    @Override
    public List<SupplierPurchaseOrder> purchaseOrders() {
        return database.execute("supplier.purchase_orders.all", true,
                () -> purchaseOrders.findAllByOrderByCreatedAtDesc().stream()
                        .map(SupplierPurchaseOrderDocument::toDomain).toList());
    }

    @Override
    public SupplierPurchaseOrder ensurePurchaseOrder(Order order) {
        try {
            return database.execute("supplier.purchase_order.ensure", true, () -> transactions.execute(ignored ->
                    purchaseOrders.findByOrderId(order.id()).map(SupplierPurchaseOrderDocument::toDomain)
                            .orElseGet(() -> purchaseOrders.insert(new SupplierPurchaseOrderDocument(
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
                var document = purchaseOrders.findById(purchaseOrderId)
                        .orElseThrow(() -> new NotFoundException("Unknown supplier purchase order " + purchaseOrderId));
                if (document.status == SupplierPurchaseOrder.Status.PROCESSED) return document.toDomain();
                long actualVersion = document.version == null ? 0 : document.version;
                if (actualVersion != expectedVersion) {
                    throw new StoreConflictException("Purchase order changed in another request; refresh and retry");
                }
                document.markProcessed(Instant.now(clock));
                var processed = purchaseOrders.save(document).toDomain();
                template.updateFirst(Query.query(Criteria.where("_id").is(document.orderId)
                                .and("status").is(Order.APPROVED)),
                        Update.update("status", Order.COMPLETED).inc("version", 1), OrderDocument.class);
                return processed;
            }));
        } catch (OptimisticLockingFailureException conflict) {
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
