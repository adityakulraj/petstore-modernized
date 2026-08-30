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
    /** Creates a customer notification service and wires its required collaborators. */
    public CustomerNotificationService(CustomerNotificationStore store) {
        this(store, Clock.systemUTC());
    }

    /** Creates a customer notification service and wires its required collaborators. */
    CustomerNotificationService(CustomerNotificationStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** Coordinates the notifications application use case. */
    public List<CustomerNotification> notifications(String customerId) {
        return store.notifications(customerId);
    }

    /** Coordinates the mark read application use case. */
    public CustomerNotification markRead(String customerId, String notificationId, long expectedVersion) {
        return store.markRead(customerId, notificationId, expectedVersion, Instant.now(clock));
    }
}
