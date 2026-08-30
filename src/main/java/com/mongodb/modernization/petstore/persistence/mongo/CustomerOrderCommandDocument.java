package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("customerOrderCommands")
@CompoundIndex(name = "ix_customer_order_command_order", def = "{'customerId': 1, 'orderId': 1, 'createdAt': -1}")
class CustomerOrderCommandDocument {
    @Id String id;
    String customerId;
    String idempotencyKey;
    String orderId;
    String action;
    long expectedVersion;
    String reason;
    String resultStatus;
    long resultVersion;
    Instant createdAt;

    CustomerOrderCommandDocument() {}
    CustomerOrderCommandDocument(String customerId, String key, String orderId, String action, long expectedVersion,
                                 String reason, String resultStatus, long resultVersion, Instant createdAt) {
        this.id = customerId + ":" + key; this.customerId = customerId; this.idempotencyKey = key;
        this.orderId = orderId; this.action = action; this.expectedVersion = expectedVersion; this.reason = reason;
        this.resultStatus = resultStatus; this.resultVersion = resultVersion; this.createdAt = createdAt;
    }

    boolean matches(String requestedOrderId, String requestedAction, long requestedVersion, String requestedReason) {
        return orderId.equals(requestedOrderId) && action.equals(requestedAction)
                && expectedVersion == requestedVersion && reason.equals(requestedReason);
    }
}
