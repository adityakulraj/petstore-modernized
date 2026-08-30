package com.mongodb.modernization.petstore.supplier.application;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;

import java.util.List;

public interface SupplierStore {
    /** Executes the inventory persistence operation against the selected database. */
    List<Product> inventory();
    /** Executes the replace inventory persistence operation against the selected database. */
    Product replaceInventory(String productId, long expectedVersion, int quantity, String idempotencyKey);
    /** Executes the backorders persistence operation against the selected database. */
    List<Order> backorders();
    /** Executes the purchase orders persistence operation against the selected database. */
    List<SupplierPurchaseOrder> purchaseOrders();
    /** Executes the ensure purchase order persistence operation against the selected database. */
    SupplierPurchaseOrder ensurePurchaseOrder(Order order);
    /** Executes the process purchase order persistence operation against the selected database. */
    SupplierPurchaseOrder processPurchaseOrder(String purchaseOrderId, long expectedVersion);
}
