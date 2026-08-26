package com.mongodb.modernization.petstore.orders.domain;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.cart.domain.CartLine;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {
    private static final Address ADDRESS = new Address("Alice", "1 Main", "", "Pune", "MH", "411001", "India");

    @Test
    void autoApprovesOnlyOrdersBelowTheConfiguredThreshold() {
        assertThat(order("499.99").status()).isEqualTo(Order.APPROVED);
        assertThat(order("500.00").status()).isEqualTo(Order.PENDING);
        assertThat(order("500.01").status()).isEqualTo(Order.PENDING);
    }

    @Test
    void backorderRetainsSnapshotsAndReentersTheNormalApprovalPolicyAfterAllocation() {
        var cart = new Cart("cart", "alice", 1,
                List.of(new CartLine("item", "Item", new BigDecimal("500.00"), 1)));
        var order = Order.backordered("order", "alice", "key", Instant.EPOCH, ADDRESS, cart);

        assertThat(order.status()).isEqualTo(Order.BACKORDERED);
        assertThat(order.lines()).hasSize(1);
        assertThat(order.statusAfterInventoryAllocation(new BigDecimal("500.00"))).isEqualTo(Order.PENDING);
    }

    private static Order order(String total) {
        var price = new BigDecimal(total);
        var cart = new Cart("cart", "alice", 1, List.of(new CartLine("item", "Item", price, 1)));
        return Order.submitted("order", "alice", "key", Instant.EPOCH, ADDRESS, cart, new BigDecimal("500.00"));
    }
}
