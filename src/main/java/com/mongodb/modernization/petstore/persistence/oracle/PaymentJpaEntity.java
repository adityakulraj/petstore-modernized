package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.payments.domain.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "PS_PAYMENT", indexes = @Index(name = "IX_PS_PAYMENT_CUSTOMER_CREATED", columnList = "CUSTOMER_ID,CREATED_AT"))
class PaymentJpaEntity {
    @Id @Column(length = 36) String id;
    @Column(name = "ORDER_ID", nullable = false, unique = true, length = 36) String orderId;
    @Column(name = "CUSTOMER_ID", nullable = false, length = 100) String customerId;
    @Column(nullable = false, precision = 12, scale = 2) BigDecimal amount;
    @Column(nullable = false, length = 3) String currency;
    @Column(name = "METHOD_LABEL", nullable = false, length = 80) String methodLabel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) Payment.Status status;
    @Column(name = "AUTHORIZATION_REFERENCE", nullable = false, length = 80) String authorizationReference;
    @Column(name = "CAPTURE_REFERENCE", length = 80) String captureReference;
    @Column(name = "REFUND_REFERENCE", length = 80) String refundReference;
    @Column(name = "CREATED_AT", nullable = false) Instant createdAt;
    @Column(name = "AUTHORIZED_AT", nullable = false) Instant authorizedAt;
    @Column(name = "CAPTURED_AT") Instant capturedAt;
    @Column(name = "VOIDED_AT") Instant voidedAt;
    @Column(name = "REFUNDED_AT") Instant refundedAt;
    @Version long version;

    protected PaymentJpaEntity() {}
    PaymentJpaEntity(Payment payment) { replaceWith(payment); }

    void replaceWith(Payment payment) {
        id = payment.id(); orderId = payment.orderId(); customerId = payment.customerId();
        amount = payment.amount(); currency = payment.currency(); methodLabel = payment.methodLabel();
        status = payment.status(); authorizationReference = payment.authorizationReference();
        captureReference = payment.captureReference(); refundReference = payment.refundReference();
        createdAt = payment.createdAt(); authorizedAt = payment.authorizedAt(); capturedAt = payment.capturedAt();
        voidedAt = payment.voidedAt(); refundedAt = payment.refundedAt();
    }

    Payment toDomain() {
        return new Payment(id, orderId, customerId, amount, currency, methodLabel, status,
                authorizationReference, captureReference, refundReference, createdAt, authorizedAt,
                capturedAt, voidedAt, refundedAt, version);
    }
}
