package com.mongodb.modernization.petstore.persistence.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "PS_CUSTOMER_ORDER_COMMAND",
        indexes = @Index(name = "IX_PS_CUST_ORDER_CMD_ORDER", columnList = "CUSTOMER_ID,ORDER_ID,CREATED_AT"))
class CustomerOrderCommandJpaEntity {
    @Id @Column(length = 220) String id;
    @Column(name = "CUSTOMER_ID", nullable = false, length = 100) String customerId;
    @Column(name = "IDEMPOTENCY_KEY", nullable = false, length = 100) String idempotencyKey;
    @Column(name = "ORDER_ID", nullable = false, length = 36) String orderId;
    @Column(nullable = false, length = 10) String action;
    @Column(name = "EXPECTED_VERSION", nullable = false) long expectedVersion;
    @Column(nullable = false, length = 250) String reason;
    @Column(name = "RESULT_STATUS", nullable = false, length = 30) String resultStatus;
    @Column(name = "RESULT_VERSION", nullable = false) long resultVersion;
    @Column(name = "CREATED_AT", nullable = false) Instant createdAt;

    protected CustomerOrderCommandJpaEntity() {}
    CustomerOrderCommandJpaEntity(String customerId, String key, String orderId, String action, long expectedVersion,
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
