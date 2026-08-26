package com.mongodb.modernization.petstore.supplier.application;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;

import java.util.List;

public interface SupplierStore {
    List<Product> inventory();
    Product replaceInventory(String productId, long expectedVersion, int quantity, String idempotencyKey);
    List<Order> backorders();
    List<SupplierPurchaseOrder> purchaseOrders();
    SupplierPurchaseOrder ensurePurchaseOrder(Order order);
    SupplierPurchaseOrder processPurchaseOrder(String purchaseOrderId, long expectedVersion);
}
