package com.mongodb.modernization.petstore.shared.application;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.domain.Address;
import com.mongodb.modernization.petstore.supplier.application.SupplierService;
import com.mongodb.modernization.petstore.supplier.application.SupplierStore;
import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontServiceTest {
    @Test
    void returnsExistingOrderAndRepairsSupplierHandOffWithoutExecutingCheckoutAgain() {
        var address = new Address("Alice", "1 Main", "", "Pune", "MH", "411001", "India");
        var order = new Order("order-1", "alice", "same-key", Instant.parse("2026-08-26T00:00:00Z"),
                Order.APPROVED, address, List.of(), BigDecimal.ZERO, 0, null, null);
        var storefront = new ExistingOrderStore(order);
        var supplier = new RecordingSupplierStore();
        var service = new StorefrontService(storefront, new SupplierService(supplier));

        assertThat(service.checkout("alice", 7, "same-key", address)).isSameAs(order);
        assertThat(storefront.checkoutCalls).isZero();
        assertThat(supplier.ensured).isSameAs(order);
    }

    private static final class ExistingOrderStore implements StorefrontStore {
        private final Order existing;
        private int checkoutCalls;
        private ExistingOrderStore(Order existing) { this.existing = existing; }
        @Override public Optional<Order> orderByIdempotencyKey(String customerId, String key) { return Optional.of(existing); }
        @Override public Order checkout(String customerId, long version, String key, Address address) { checkoutCalls++; return existing; }
        @Override public List<Product> products(String categoryId) { throw unsupported(); }
        @Override public Optional<Product> product(String productId) { throw unsupported(); }
        @Override public Cart cart(String customerId) { throw unsupported(); }
        @Override public Cart addToCart(String customerId, long version, String productId, int quantity) { throw unsupported(); }
        @Override public Cart updateCart(String customerId, long version, String productId, int quantity) { throw unsupported(); }
        @Override public Cart removeFromCart(String customerId, long version, String productId) { throw unsupported(); }
        @Override public List<Order> orders(String customerId) { throw unsupported(); }
        @Override public void seedIfEmpty() { throw unsupported(); }
    }

    private static final class RecordingSupplierStore implements SupplierStore {
        private Order ensured;
        @Override public SupplierPurchaseOrder ensurePurchaseOrder(Order order) { ensured = order; return SupplierPurchaseOrder.ready(order); }
        @Override public List<Product> inventory() { throw unsupported(); }
        @Override public Product replaceInventory(String productId, long version, int quantity, String key) { throw unsupported(); }
        @Override public List<Order> backorders() { return List.of(); }
        @Override public List<SupplierPurchaseOrder> purchaseOrders() { throw unsupported(); }
        @Override public SupplierPurchaseOrder processPurchaseOrder(String id, long version) { throw unsupported(); }
    }

    private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException("Not used in this test"); }
}
