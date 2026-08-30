package com.mongodb.modernization.petstore.notifications.application;

import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.orders.domain.Order;

import java.time.Instant;
import java.util.List;

public interface CustomerNotificationStore {
    /** Executes the enqueue persistence operation against the selected database. */
    CustomerNotification enqueue(Order order, CustomerNotification.Type type, Instant occurredAt);
    /** Executes the notifications persistence operation against the selected database. */
    List<CustomerNotification> notifications(String customerId);
    /** Executes the mark read persistence operation against the selected database. */
    CustomerNotification markRead(String customerId, String notificationId, long expectedVersion, Instant readAt);
    /** Executes the ready for delivery persistence operation against the selected database. */
    List<CustomerNotification> readyForDelivery(Instant now, int limit);
    /** Executes the mark delivered persistence operation against the selected database. */
    boolean markDelivered(String notificationId, long expectedVersion, Instant deliveredAt);
    /** Executes the record delivery failure persistence operation against the selected database. */
    boolean recordDeliveryFailure(String notificationId, long expectedVersion, int attempts,
                                  Instant nextAttemptAt, String error);
}
