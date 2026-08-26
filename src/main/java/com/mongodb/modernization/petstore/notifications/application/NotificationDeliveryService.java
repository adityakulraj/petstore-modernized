package com.mongodb.modernization.petstore.notifications.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class NotificationDeliveryService {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
    private final CustomerNotificationStore store;
    private final NotificationDeliveryGateway gateway;
    private final Clock clock;

    @Autowired
    public NotificationDeliveryService(CustomerNotificationStore store, NotificationDeliveryGateway gateway) {
        this(store, gateway, Clock.systemUTC());
    }

    NotificationDeliveryService(CustomerNotificationStore store, NotificationDeliveryGateway gateway, Clock clock) {
        this.store = store;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-interval:1s}")
    public void deliverReady() {
        Instant now = Instant.now(clock);
        for (var notification : store.readyForDelivery(now, 50)) {
            try {
                gateway.deliver(notification);
                store.markDelivered(notification.id(), notification.version(), now);
            } catch (RuntimeException failure) {
                int attempts = notification.deliveryAttempts() + 1;
                Instant nextAttempt = now.plus(backoff(attempts));
                String error = safeError(failure);
                store.recordDeliveryFailure(notification.id(), notification.version(), attempts, nextAttempt, error);
                LOG.atWarn()
                        .addKeyValue("event", "notification.delivery.retry_scheduled")
                        .addKeyValue("notificationId", notification.id())
                        .addKeyValue("attempt", attempts)
                        .addKeyValue("nextAttemptAt", nextAttempt)
                        .setCause(failure)
                        .log("Customer notification delivery failed; retry scheduled");
            }
        }
    }

    static Duration backoff(int attempts) {
        long seconds = 1L << Math.min(Math.max(attempts - 1, 0), 9);
        return Duration.ofSeconds(Math.min(seconds, MAX_BACKOFF.toSeconds()));
    }

    private static String safeError(RuntimeException failure) {
        String value = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return value.substring(0, Math.min(value.length(), 500));
    }
}
