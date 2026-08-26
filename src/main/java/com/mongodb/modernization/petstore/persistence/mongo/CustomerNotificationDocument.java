package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("customerNotifications")
@CompoundIndexes({
        @CompoundIndex(name = "ix_notification_customer_created", def = "{'customerId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "ix_notification_delivery_due", def = "{'deliveryStatus': 1, 'nextAttemptAt': 1}")
})
class CustomerNotificationDocument {
    @Id String id;
    String customerId;
    String orderId;
    CustomerNotification.Type type;
    String title;
    String message;
    Instant createdAt;
    CustomerNotification.DeliveryStatus deliveryStatus;
    int deliveryAttempts;
    Instant nextAttemptAt;
    Instant deliveredAt;
    String lastError;
    Instant readAt;
    long version;

    CustomerNotificationDocument() {}

    CustomerNotificationDocument(CustomerNotification notification) {
        id = notification.id(); customerId = notification.customerId(); orderId = notification.orderId();
        type = notification.type(); title = notification.title(); message = notification.message();
        createdAt = notification.createdAt(); deliveryStatus = notification.deliveryStatus();
        deliveryAttempts = notification.deliveryAttempts(); nextAttemptAt = notification.nextAttemptAt();
        deliveredAt = notification.deliveredAt(); lastError = notification.lastError();
        readAt = notification.readAt(); version = notification.version();
    }

    CustomerNotification toDomain() {
        return new CustomerNotification(id, customerId, orderId, type, title, message, createdAt,
                deliveryStatus, deliveryAttempts, nextAttemptAt, deliveredAt, lastError, readAt, version);
    }
}
