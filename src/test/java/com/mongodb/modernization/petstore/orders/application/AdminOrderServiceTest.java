package com.mongodb.modernization.petstore.orders.application;

import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.domain.Address;
import com.mongodb.modernization.petstore.supplier.application.SupplierService;
import com.mongodb.modernization.petstore.supplier.application.SupplierStore;
import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminOrderServiceTest {
    @Test
    void approvalEnsuresSupplierHandOffButDenialDoesNot() {
        var approved = order(Order.APPROVED);
        var supplier = new RecordingSupplierStore();
        var service = new AdminOrderService(new ReturningAdminStore(approved), new SupplierService(supplier));

        assertThat(service.review(approved.id(), 0, AdminOrderStore.Decision.APPROVED, "admin")).isSameAs(approved);
        assertThat(supplier.ensured).isSameAs(approved);

        supplier.ensured = null;
        var denied = order(Order.DENIED);
        service = new AdminOrderService(new ReturningAdminStore(denied), new SupplierService(supplier));
        assertThat(service.review(denied.id(), 0, AdminOrderStore.Decision.DENIED, "admin")).isSameAs(denied);
        assertThat(supplier.ensured).isNull();
    }

    private static Order order(String status) {
        var address = new Address("Alice", "1 Main", "", "Pune", "MH", "411001", "India");
        return new Order("order-1", "alice", "key", Instant.EPOCH, status, address, List.of(),
                BigDecimal.ZERO, 1, Instant.EPOCH, "admin");
    }

    private record ReturningAdminStore(Order result) implements AdminOrderStore {
        @Override public List<Order> orders() { return List.of(result); }
        @Override public Order review(String orderId, long expectedVersion, Decision decision, String reviewer) { return result; }
    }

    private static final class RecordingSupplierStore implements SupplierStore {
        private Order ensured;
        @Override public SupplierPurchaseOrder ensurePurchaseOrder(Order order) { ensured = order; return SupplierPurchaseOrder.ready(order); }
        @Override public List<Product> inventory() { throw unsupported(); }
        @Override public Product replaceInventory(String productId, long expectedVersion, int quantity, String key) { throw unsupported(); }
        @Override public List<Order> backorders() { return List.of(); }
        @Override public List<SupplierPurchaseOrder> purchaseOrders() { throw unsupported(); }
        @Override public SupplierPurchaseOrder processPurchaseOrder(String purchaseOrderId, long expectedVersion) { throw unsupported(); }
    }

    private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException("Not used"); }
}
