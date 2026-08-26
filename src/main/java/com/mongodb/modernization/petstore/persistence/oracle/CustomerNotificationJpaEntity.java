package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "PS_CUSTOMER_NOTIFICATION",
        uniqueConstraints = @UniqueConstraint(name = "UK_PS_NOTIFICATION_ORDER_TYPE", columnNames = {"ORDER_ID", "TYPE"}),
        indexes = {
                @Index(name = "IX_PS_NOTIFICATION_CUSTOMER", columnList = "CUSTOMER_ID,CREATED_AT"),
                @Index(name = "IX_PS_NOTIFICATION_DELIVERY", columnList = "DELIVERY_STATUS,NEXT_ATTEMPT_AT")
        })
class CustomerNotificationJpaEntity {
    @Id @Column(length = 80) String id;
    @Column(name = "CUSTOMER_ID", nullable = false, length = 100) String customerId;
    @Column(name = "ORDER_ID", nullable = false, length = 36) String orderId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) CustomerNotification.Type type;
    @Column(nullable = false, length = 120) String title;
    @Column(nullable = false, length = 500) String message;
    @Column(name = "CREATED_AT", nullable = false) Instant createdAt;
    @Enumerated(EnumType.STRING) @Column(name = "DELIVERY_STATUS", nullable = false, length = 20)
    CustomerNotification.DeliveryStatus deliveryStatus;
    @Column(name = "DELIVERY_ATTEMPTS", nullable = false) int deliveryAttempts;
    @Column(name = "NEXT_ATTEMPT_AT") Instant nextAttemptAt;
    @Column(name = "DELIVERED_AT") Instant deliveredAt;
    @Column(name = "LAST_ERROR", length = 500) String lastError;
    @Column(name = "READ_AT") Instant readAt;
    @Version long version;

    protected CustomerNotificationJpaEntity() {}

    CustomerNotificationJpaEntity(CustomerNotification notification) {
        id = notification.id(); customerId = notification.customerId(); orderId = notification.orderId();
        type = notification.type(); title = notification.title(); message = notification.message();
        createdAt = notification.createdAt(); deliveryStatus = notification.deliveryStatus();
        deliveryAttempts = notification.deliveryAttempts(); nextAttemptAt = notification.nextAttemptAt();
        deliveredAt = notification.deliveredAt(); lastError = notification.lastError(); readAt = notification.readAt();
    }

    CustomerNotification toDomain() {
        return new CustomerNotification(id, customerId, orderId, type, title, message, createdAt,
                deliveryStatus, deliveryAttempts, nextAttemptAt, deliveredAt, lastError, readAt, version);
    }

    void markRead(Instant when) { readAt = when; }

    void markDelivered(Instant when) {
        deliveryStatus = CustomerNotification.DeliveryStatus.DELIVERED;
        deliveredAt = when;
        nextAttemptAt = null;
        lastError = null;
        deliveryAttempts++;
    }

    void recordFailure(int attempts, Instant retryAt, String error) {
        deliveryAttempts = attempts;
        nextAttemptAt = retryAt;
        lastError = error;
    }
}
