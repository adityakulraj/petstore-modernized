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

    public SupplierService(SupplierStore store) { this.store = store; }

    public List<Product> inventory() { return store.inventory(); }

    public Product replaceInventory(String productId, long expectedVersion, int quantity) {
        var product = store.replaceInventory(productId, expectedVersion, quantity);
        LOG.atInfo().addKeyValue("event", "supplier.inventory.updated")
                .addKeyValue("productId", product.id()).addKeyValue("quantity", product.stock())
                .addKeyValue("inventoryVersion", product.version()).log("Supplier inventory updated");
        return product;
    }

    public List<SupplierPurchaseOrder> purchaseOrders() { return store.purchaseOrders(); }

    public SupplierPurchaseOrder ensurePurchaseOrder(Order order) {
        var purchaseOrder = store.ensurePurchaseOrder(order);
        LOG.atInfo().addKeyValue("event", "supplier.purchase_order.ready")
                .addKeyValue("purchaseOrderId", purchaseOrder.id()).addKeyValue("orderId", order.id())
                .addKeyValue("lineCount", purchaseOrder.lines().size()).log("Supplier purchase order is ready");
        return purchaseOrder;
    }

    public SupplierPurchaseOrder processPurchaseOrder(String id, long expectedVersion) {
        var purchaseOrder = store.processPurchaseOrder(id, expectedVersion);
        LOG.atInfo().addKeyValue("event", "supplier.purchase_order.processed")
                .addKeyValue("purchaseOrderId", purchaseOrder.id()).addKeyValue("orderId", purchaseOrder.orderId())
                .addKeyValue("purchaseOrderVersion", purchaseOrder.version()).log("Supplier purchase order processed");
        return purchaseOrder;
    }
}
