package com.mongodb.modernization.petstore.orders.domain;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.shared.domain.Address;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Immutable order snapshot containing checkout prices, lifecycle state, and review metadata. */
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

    /** Defensively copies order lines so historical checkout data cannot be mutated. */
    public Order {
        lines = List.copyOf(lines);
    }

    /** Creates a submitted order using the default administrator-approval threshold. */
    public static Order placed(String id, String customerId, String idempotencyKey, Instant createdAt,
                               Address address, Cart cart) {
        return submitted(id, customerId, idempotencyKey, createdAt, address, cart,
                new BigDecimal("500.00"));
    }

    /** Creates an approved or pending order based on its total and the configured review threshold. */
    public static Order submitted(String id, String customerId, String idempotencyKey, Instant createdAt,
                                  Address address, Cart cart, BigDecimal approvalThreshold) {
        var status = cart.total().compareTo(approvalThreshold) < 0 ? APPROVED : PENDING;
        return new Order(id, customerId, idempotencyKey, createdAt, status, address,
                cart.lines().stream().map(OrderLine::from).toList(), cart.total(), 0, null, null);
    }

    /** Creates an order that retains its checkout snapshot while waiting for inventory. */
    public static Order backordered(String id, String customerId, String idempotencyKey, Instant createdAt,
                                    Address address, Cart cart) {
        return new Order(id, customerId, idempotencyKey, createdAt, BACKORDERED, address,
                cart.lines().stream().map(OrderLine::from).toList(), cart.total(), 0, null, null);
    }

    /** Determines whether a replenished order can auto-approve or still requires administrator review. */
    public String statusAfterInventoryAllocation(BigDecimal approvalThreshold) {
        return total.compareTo(approvalThreshold) < 0 ? APPROVED : PENDING;
    }

    /** Reports whether the order may have a supplier purchase order. */
    public boolean supplierReady() {
        return APPROVED.equals(status) || COMPLETED.equals(status);
    }

    /** Reports whether cancellation is legal before fulfilment completes. */
    public boolean cancellable() {
        return BACKORDERED.equals(status) || PENDING.equals(status) || APPROVED.equals(status);
    }
}
