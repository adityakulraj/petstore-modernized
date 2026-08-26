package com.mongodb.modernization.petstore.notifications.application;

import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;

public interface NotificationDeliveryGateway {
    void deliver(CustomerNotification notification);
}
