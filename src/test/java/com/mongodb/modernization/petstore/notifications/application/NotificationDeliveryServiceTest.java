package com.mongodb.modernization.petstore.notifications.application;

import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");

    @Test
    void successfulDeliveryIsAcknowledgedWithTheOptimisticToken() {
        var notification = notification(2, 0);
        var store = new FakeStore(notification);
        var delivered = new java.util.concurrent.atomic.AtomicReference<CustomerNotification>();
        NotificationDeliveryGateway gateway = delivered::set;

        new NotificationDeliveryService(store, gateway, Clock.fixed(NOW, ZoneOffset.UTC)).deliverReady();

        assertThat(delivered).hasValue(notification);
        assertThat(store.deliveredId).isEqualTo(notification.id());
        assertThat(store.deliveredVersion).isEqualTo(2);
        assertThat(store.deliveredAt).isEqualTo(NOW);
    }

    @Test
    void failureIsPersistedWithBoundedExponentialBackoff() {
        var notification = notification(4, 3);
        var store = new FakeStore(notification);
        NotificationDeliveryGateway gateway = ignored -> { throw new IllegalStateException("provider unavailable"); };

        new NotificationDeliveryService(store, gateway, Clock.fixed(NOW, ZoneOffset.UTC)).deliverReady();

        assertThat(store.failedId).isEqualTo(notification.id());
        assertThat(store.failedVersion).isEqualTo(4);
        assertThat(store.failedAttempts).isEqualTo(4);
        assertThat(store.retryAt).isEqualTo(NOW.plusSeconds(8));
        assertThat(store.error).isEqualTo("provider unavailable");
        assertThat(NotificationDeliveryService.backoff(20)).isEqualTo(Duration.ofMinutes(5));
    }

    private static CustomerNotification notification(long version, int attempts) {
        return new CustomerNotification("order:ORDER_APPROVED", "alice", "order",
                CustomerNotification.Type.ORDER_APPROVED, "Approved", "Approved", NOW,
                CustomerNotification.DeliveryStatus.PENDING, attempts, NOW, null, null, null, version);
    }

    private static final class FakeStore implements CustomerNotificationStore {
        private final CustomerNotification ready;
        String deliveredId; long deliveredVersion; Instant deliveredAt;
        String failedId; long failedVersion; int failedAttempts; Instant retryAt; String error;

        private FakeStore(CustomerNotification ready) { this.ready = ready; }
        @Override public List<CustomerNotification> readyForDelivery(Instant now, int limit) { return List.of(ready); }
        @Override public boolean markDelivered(String id, long version, Instant when) {
            deliveredId = id; deliveredVersion = version; deliveredAt = when; return true;
        }
        @Override public boolean recordDeliveryFailure(String id, long version, int attempts, Instant next, String reason) {
            failedId = id; failedVersion = version; failedAttempts = attempts; retryAt = next; error = reason; return true;
        }
        @Override public CustomerNotification enqueue(com.mongodb.modernization.petstore.orders.domain.Order order,
                CustomerNotification.Type type, Instant occurredAt) { throw new UnsupportedOperationException(); }
        @Override public List<CustomerNotification> notifications(String customerId) { throw new UnsupportedOperationException(); }
        @Override public CustomerNotification markRead(String customerId, String id, long version, Instant readAt) {
            throw new UnsupportedOperationException();
        }
    }
}
