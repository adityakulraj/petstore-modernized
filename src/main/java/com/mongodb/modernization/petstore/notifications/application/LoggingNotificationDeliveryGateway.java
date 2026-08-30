package com.mongodb.modernization.petstore.notifications.application;

import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingNotificationDeliveryGateway implements NotificationDeliveryGateway {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingNotificationDeliveryGateway.class);

    @Override
    /** Delivers to the local in-app channel and logs the deterministic notification idempotency key. */
    public void deliver(CustomerNotification notification) {
        // The deterministic notification id is also the idempotency key for a future e-mail provider adapter.
        LOG.atInfo()
                .addKeyValue("event", "notification.delivery")
                .addKeyValue("notificationId", notification.id())
                .addKeyValue("customerId", notification.customerId())
                .addKeyValue("orderId", notification.orderId())
                .addKeyValue("notificationType", notification.type())
                .log("Customer order notification delivered to the in-app channel");
    }
}
