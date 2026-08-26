package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.notifications.application.CustomerNotificationStore;
import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Repository
@Profile("oracle")
class OracleCustomerNotificationStore implements CustomerNotificationStore {
    private final JpaCustomerNotificationRepository notifications;
    private final TransactionTemplate transactions;
    private final DatabaseExecutor database;

    OracleCustomerNotificationStore(JpaCustomerNotificationRepository notifications,
                                    PlatformTransactionManager transactionManager, DatabaseExecutor database) {
        this.notifications = notifications;
        this.transactions = new TransactionTemplate(transactionManager);
        this.database = database;
    }

    @Override
    public CustomerNotification enqueue(Order order, CustomerNotification.Type type, Instant occurredAt) {
        // The enclosing business transaction owns retry of the complete state transition.
        return database.execute("notifications.enqueue", false, () -> {
            var notification = CustomerNotification.forOrder(order, type, occurredAt);
            return notifications.findById(notification.id()).map(CustomerNotificationJpaEntity::toDomain)
                    .orElseGet(() -> notifications.saveAndFlush(new CustomerNotificationJpaEntity(notification)).toDomain());
        });
    }

    @Override
    public List<CustomerNotification> notifications(String customerId) {
        return database.execute("notifications.by_customer", true,
                () -> notifications.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                        .map(CustomerNotificationJpaEntity::toDomain).toList());
    }

    @Override
    public CustomerNotification markRead(String customerId, String notificationId, long expectedVersion, Instant readAt) {
        return database.execute("notifications.mark_read", true, () -> transactions.execute(ignored -> {
            var entity = notificationForCustomer(customerId, notificationId);
            if (entity.readAt != null) return entity.toDomain();
            if (entity.version != expectedVersion) {
                throw new StoreConflictException("Notification changed in another request; refresh and retry");
            }
            entity.markRead(readAt);
            return notifications.saveAndFlush(entity).toDomain();
        }));
    }

    @Override
    public List<CustomerNotification> readyForDelivery(Instant now, int limit) {
        return database.execute("notifications.delivery.ready", true,
                () -> notifications.findByDeliveryStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                                CustomerNotification.DeliveryStatus.PENDING, now, PageRequest.of(0, limit)).stream()
                        .map(CustomerNotificationJpaEntity::toDomain).toList());
    }

    @Override
    public boolean markDelivered(String notificationId, long expectedVersion, Instant deliveredAt) {
        return database.execute("notifications.delivery.complete", true, () -> transactions.execute(ignored -> {
            var entity = notifications.findById(notificationId).orElse(null);
            if (entity == null || entity.deliveryStatus != CustomerNotification.DeliveryStatus.PENDING
                    || entity.version != expectedVersion) return false;
            entity.markDelivered(deliveredAt);
            notifications.saveAndFlush(entity);
            return true;
        }));
    }

    @Override
    public boolean recordDeliveryFailure(String notificationId, long expectedVersion, int attempts,
                                         Instant nextAttemptAt, String error) {
        return database.execute("notifications.delivery.retry", true, () -> transactions.execute(ignored -> {
            var entity = notifications.findById(notificationId).orElse(null);
            if (entity == null || entity.deliveryStatus != CustomerNotification.DeliveryStatus.PENDING
                    || entity.version != expectedVersion) return false;
            entity.recordFailure(attempts, nextAttemptAt, error);
            notifications.saveAndFlush(entity);
            return true;
        }));
    }

    private CustomerNotificationJpaEntity notificationForCustomer(String customerId, String id) {
        var entity = notifications.findById(id).orElseThrow(() -> new NotFoundException("Unknown notification " + id));
        if (!entity.customerId.equals(customerId)) throw new NotFoundException("Unknown notification " + id);
        return entity;
    }
}
