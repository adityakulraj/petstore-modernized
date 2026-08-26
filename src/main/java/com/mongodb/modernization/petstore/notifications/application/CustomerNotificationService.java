package com.mongodb.modernization.petstore.notifications.application;

import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class CustomerNotificationService {
    private final CustomerNotificationStore store;
    private final Clock clock;

    @Autowired
    public CustomerNotificationService(CustomerNotificationStore store) {
        this(store, Clock.systemUTC());
    }

    CustomerNotificationService(CustomerNotificationStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public List<CustomerNotification> notifications(String customerId) {
        return store.notifications(customerId);
    }

    public CustomerNotification markRead(String customerId, String notificationId, long expectedVersion) {
        return store.markRead(customerId, notificationId, expectedVersion, Instant.now(clock));
    }
}
