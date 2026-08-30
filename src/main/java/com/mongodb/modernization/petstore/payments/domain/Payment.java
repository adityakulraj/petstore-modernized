package com.mongodb.modernization.petstore.payments.domain;

import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.payments.application.PaymentDeclinedException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;

import java.math.BigDecimal;
import java.time.Instant;

/** Payment ledger. Only an opaque method label and provider references are retained; card data is never stored. */
public record Payment(String id, String orderId, String customerId, BigDecimal amount, String currency,
                      String methodLabel, Status status, String authorizationReference,
                      String captureReference, String refundReference, Instant createdAt,
                      Instant authorizedAt, Instant capturedAt, Instant voidedAt, Instant refundedAt,
                      long version) {
    public static final String APPROVED_DEMO_TOKEN = "tok_demo_visa";
    public static final String DECLINED_DEMO_TOKEN = "tok_demo_declined";

    public enum Status { AUTHORIZED, CAPTURED, VOIDED, REFUNDED }

    /** Validates the opaque demo token and creates an authorization without retaining card data. */
    public static Payment authorize(Order order, String token, Instant when) {
        String normalized = token == null || token.isBlank() ? APPROVED_DEMO_TOKEN : token.trim();
        if (DECLINED_DEMO_TOKEN.equals(normalized)) {
            throw new PaymentDeclinedException("The demo payment method was declined; choose the approved demo method and retry");
        }
        if (!APPROVED_DEMO_TOKEN.equals(normalized)) {
            throw new PaymentDeclinedException("Unknown payment token; no card data was accepted or stored");
        }
        return new Payment(order.id(), order.id(), order.customerId(), order.total(), "USD",
                "Demo Visa ending 4242", Status.AUTHORIZED, "demo-auth-" + order.id(),
                null, null, when, when, null, null, null, 0);
    }

    /** Idempotently captures an authorized payment after supplier fulfilment. */
    public Payment capture(Instant when) {
        if (status == Status.CAPTURED) return this;
        if (status != Status.AUTHORIZED) throw new StoreConflictException("Only an authorized payment can be captured");
        return new Payment(id, orderId, customerId, amount, currency, methodLabel, Status.CAPTURED,
                authorizationReference, "demo-capture-" + orderId, refundReference, createdAt,
                authorizedAt, when, voidedAt, refundedAt, version);
    }

    /** Idempotently voids an uncaptured authorization when its order is cancelled. */
    public Payment voidAuthorization(Instant when) {
        if (status == Status.VOIDED) return this;
        if (status != Status.AUTHORIZED) throw new StoreConflictException("Only an authorized payment can be voided");
        return new Payment(id, orderId, customerId, amount, currency, methodLabel, Status.VOIDED,
                authorizationReference, captureReference, refundReference, createdAt,
                authorizedAt, capturedAt, when, refundedAt, version);
    }

    /** Idempotently refunds a captured payment for a completed order. */
    public Payment refund(Instant when) {
        if (status == Status.REFUNDED) return this;
        if (status != Status.CAPTURED) throw new StoreConflictException("Only a captured payment can be refunded");
        return new Payment(id, orderId, customerId, amount, currency, methodLabel, Status.REFUNDED,
                authorizationReference, captureReference, "demo-refund-" + orderId, createdAt,
                authorizedAt, capturedAt, voidedAt, when, version);
    }
}
