package com.mongodb.modernization.petstore.notifications.domain;

import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerNotificationTest {
    @Test
    void eventIdentityIsDeterministicForAnOrderTransition() {
        var order = new Order("order-123", "alice", "key", Instant.parse("2026-08-26T10:00:00Z"),
                Order.PENDING, new Address("Alice", "1 Main", "", "Pune", "MH", "411001", "India"),
                List.of(), BigDecimal.TEN, 0, null, null);

        var first = CustomerNotification.forOrder(order, CustomerNotification.Type.ORDER_PENDING, order.createdAt());
        var replay = CustomerNotification.forOrder(order, CustomerNotification.Type.ORDER_PENDING, order.createdAt());

        assertThat(first.id()).isEqualTo("order-123:ORDER_PENDING").isEqualTo(replay.id());
        assertThat(first.deliveryStatus()).isEqualTo(CustomerNotification.DeliveryStatus.PENDING);
        assertThat(first.deliveryAttempts()).isZero();
        assertThat(first.nextAttemptAt()).isEqualTo(order.createdAt());
    }

    @Test
    void backorderAndAllocationMessagesHaveSeparateIdempotencyKeys() {
        var order = new Order("order-123", "alice", "key", Instant.EPOCH,
                Order.BACKORDERED, new Address("Alice", "1 Main", "", "Pune", "MH", "411001", "India"),
                List.of(), BigDecimal.TEN, 0, null, null);

        var waiting = CustomerNotification.forOrder(order, CustomerNotification.Type.ORDER_BACKORDERED, Instant.EPOCH);
        var allocated = CustomerNotification.forOrder(order, CustomerNotification.Type.ORDER_INVENTORY_ALLOCATED,
                Instant.EPOCH.plusSeconds(1));

        assertThat(waiting.id()).endsWith(":ORDER_BACKORDERED");
        assertThat(allocated.id()).endsWith(":ORDER_INVENTORY_ALLOCATED");
        assertThat(waiting.title()).isEqualTo("Order backordered");
        assertThat(allocated.title()).isEqualTo("Inventory allocated");
    }
}
