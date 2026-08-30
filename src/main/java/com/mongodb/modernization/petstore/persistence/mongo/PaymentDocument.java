package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.payments.domain.Payment;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Document("payments")
@CompoundIndex(name = "ix_payment_customer_created", def = "{'customerId': 1, 'createdAt': -1}")
class PaymentDocument {
    @Id String id;
    String orderId;
    String customerId;
    @Field(targetType = FieldType.DECIMAL128) BigDecimal amount;
    String currency;
    String methodLabel;
    Payment.Status status;
    String authorizationReference;
    String captureReference;
    String refundReference;
    Instant createdAt;
    Instant authorizedAt;
    Instant capturedAt;
    Instant voidedAt;
    Instant refundedAt;
    @Version Long version;

    PaymentDocument() {}
    PaymentDocument(Payment payment) { replaceWith(payment); }

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
                capturedAt, voidedAt, refundedAt, version == null ? 0 : version);
    }
}
