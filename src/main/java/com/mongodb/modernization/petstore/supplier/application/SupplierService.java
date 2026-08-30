package com.mongodb.modernization.petstore.supplier.application;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupplierService {
    private static final Logger LOG = LoggerFactory.getLogger(SupplierService.class);
    private final SupplierStore store;

    /** Creates a supplier service and wires its required collaborators. */
    public SupplierService(SupplierStore store) { this.store = store; }

    /** Coordinates the inventory application use case. */
    public List<Product> inventory() { return store.inventory(); }

    /** Coordinates the replace inventory application use case. */
    public Product replaceInventory(String productId, long expectedVersion, int quantity, String idempotencyKey) {
        var product = store.replaceInventory(productId, expectedVersion, quantity, idempotencyKey);
        LOG.atInfo().addKeyValue("event", "supplier.inventory.updated")
                .addKeyValue("productId", product.id()).addKeyValue("requestedQuantity", quantity)
                .addKeyValue("resultingStock", product.stock())
                .addKeyValue("inventoryVersion", product.version()).log("Supplier inventory updated");
        return product;
    }

    /** Coordinates the backorders application use case. */
    public List<Order> backorders() { return store.backorders(); }

    /** Coordinates the purchase orders application use case. */
    public List<SupplierPurchaseOrder> purchaseOrders() { return store.purchaseOrders(); }

    /** Coordinates the ensure purchase order application use case. */
    public SupplierPurchaseOrder ensurePurchaseOrder(Order order) {
        var purchaseOrder = store.ensurePurchaseOrder(order);
        LOG.atInfo().addKeyValue("event", "supplier.purchase_order.ready")
                .addKeyValue("purchaseOrderId", purchaseOrder.id()).addKeyValue("orderId", order.id())
                .addKeyValue("lineCount", purchaseOrder.lines().size()).log("Supplier purchase order is ready");
        return purchaseOrder;
    }

    /** Coordinates the process purchase order application use case. */
    public SupplierPurchaseOrder processPurchaseOrder(String id, long expectedVersion) {
        var purchaseOrder = store.processPurchaseOrder(id, expectedVersion);
        LOG.atInfo().addKeyValue("event", "supplier.purchase_order.processed")
                .addKeyValue("purchaseOrderId", purchaseOrder.id()).addKeyValue("orderId", purchaseOrder.orderId())
                .addKeyValue("purchaseOrderVersion", purchaseOrder.version()).log("Supplier purchase order processed");
        return purchaseOrder;
    }
}
