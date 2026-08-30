package com.mongodb.modernization.petstore.notifications.application;

import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;

public interface NotificationDeliveryGateway {
    /** Sends one durable notification through the configured delivery channel. */
    void deliver(CustomerNotification notification);
}
