package com.mongodb.modernization.petstore.supplier.domain;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierPurchaseOrderTest {
    @Test
    void snapshotsAnOrderAndProcessingIsIdempotent() {
        var product = new Product("DOG-1", "DOGS", "Dogs", "Dog", "Friendly", new BigDecimal("25.00"), 3, 0);
        var cart = Cart.empty("cart", "alice", 0).add(product, 2);
        var order = Order.placed("order-1", "alice", "key", Instant.parse("2026-08-26T00:00:00Z"),
                new Address("Alice", "1 Main", "", "Pune", "MH", "411001", "India"), cart);

        var ready = SupplierPurchaseOrder.ready(order);
        var processedAt = Instant.parse("2026-08-26T00:01:00Z");
        var processed = ready.processed(processedAt);

        assertThat(ready.status()).isEqualTo(SupplierPurchaseOrder.Status.READY);
        assertThat(ready.lines()).hasSize(1);
        assertThat(processed.status()).isEqualTo(SupplierPurchaseOrder.Status.PROCESSED);
        assertThat(processed.processedAt()).isEqualTo(processedAt);
        assertThat(processed.processed(processedAt.plusSeconds(30))).isSameAs(processed);
    }
}
