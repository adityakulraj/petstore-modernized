package com.mongodb.modernization.petstore.orders.domain;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.shared.domain.Address;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Order(String id, String customerId, String idempotencyKey, Instant createdAt,
                    String status, Address shippingAddress, List<OrderLine> lines, BigDecimal total,
                    long version, Instant reviewedAt, String reviewedBy) {
    public static final String PENDING = "PENDING";
    public static final String BACKORDERED = "BACKORDERED";
    public static final String APPROVED = "APPROVED";
    public static final String DENIED = "DENIED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String REFUNDED = "REFUNDED";

    public Order {
        lines = List.copyOf(lines);
    }

    public static Order placed(String id, String customerId, String idempotencyKey, Instant createdAt,
                               Address address, Cart cart) {
        return submitted(id, customerId, idempotencyKey, createdAt, address, cart,
                new BigDecimal("500.00"));
    }

    public static Order submitted(String id, String customerId, String idempotencyKey, Instant createdAt,
                                  Address address, Cart cart, BigDecimal approvalThreshold) {
        var status = cart.total().compareTo(approvalThreshold) < 0 ? APPROVED : PENDING;
        return new Order(id, customerId, idempotencyKey, createdAt, status, address,
                cart.lines().stream().map(OrderLine::from).toList(), cart.total(), 0, null, null);
    }

    public static Order backordered(String id, String customerId, String idempotencyKey, Instant createdAt,
                                    Address address, Cart cart) {
        return new Order(id, customerId, idempotencyKey, createdAt, BACKORDERED, address,
                cart.lines().stream().map(OrderLine::from).toList(), cart.total(), 0, null, null);
    }

    public String statusAfterInventoryAllocation(BigDecimal approvalThreshold) {
        return total.compareTo(approvalThreshold) < 0 ? APPROVED : PENDING;
    }

    public boolean supplierReady() {
        return APPROVED.equals(status) || COMPLETED.equals(status);
    }

    public boolean cancellable() {
        return BACKORDERED.equals(status) || PENDING.equals(status) || APPROVED.equals(status);
    }
}
