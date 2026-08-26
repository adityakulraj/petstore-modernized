package com.mongodb.modernization.petstore.notifications.application;

import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.orders.domain.Order;

import java.time.Instant;
import java.util.List;

public interface CustomerNotificationStore {
    CustomerNotification enqueue(Order order, CustomerNotification.Type type, Instant occurredAt);
    List<CustomerNotification> notifications(String customerId);
    CustomerNotification markRead(String customerId, String notificationId, long expectedVersion, Instant readAt);
    List<CustomerNotification> readyForDelivery(Instant now, int limit);
    boolean markDelivered(String notificationId, long expectedVersion, Instant deliveredAt);
    boolean recordDeliveryFailure(String notificationId, long expectedVersion, int attempts,
                                  Instant nextAttemptAt, String error);
}
