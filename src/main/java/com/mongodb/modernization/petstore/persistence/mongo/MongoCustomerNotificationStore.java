package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.notifications.application.CustomerNotificationStore;
import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@Profile("mongo")
class MongoCustomerNotificationStore implements CustomerNotificationStore {
    private final MongoTemplate template;
    private final DatabaseExecutor database;

    /** Creates a mongo customer notification store and wires its required collaborators. */
    MongoCustomerNotificationStore(MongoTemplate template, DatabaseExecutor database) {
        this.template = template;
        this.database = database;
    }

    @Override
    /** Executes the enqueue persistence operation against the selected database. */
    public CustomerNotification enqueue(Order order, CustomerNotification.Type type, Instant occurredAt) {
        // The enclosing business transaction owns retry of the complete state transition.
        return database.execute("notifications.enqueue", false, () -> {
            var notification = CustomerNotification.forOrder(order, type, occurredAt);
            var insert = new Update()
                    .setOnInsert("customerId", notification.customerId())
                    .setOnInsert("orderId", notification.orderId())
                    .setOnInsert("type", notification.type())
                    .setOnInsert("title", notification.title())
                    .setOnInsert("message", notification.message())
                    .setOnInsert("createdAt", notification.createdAt())
                    .setOnInsert("deliveryStatus", notification.deliveryStatus())
                    .setOnInsert("deliveryAttempts", 0)
                    .setOnInsert("nextAttemptAt", notification.nextAttemptAt())
                    .setOnInsert("version", 0L);
            template.upsert(Query.query(Criteria.where("_id").is(notification.id())), insert,
                    CustomerNotificationDocument.class);
            return template.findById(notification.id(), CustomerNotificationDocument.class).toDomain();
        });
    }

    @Override
    /** Executes the notifications persistence operation against the selected database. */
    public List<CustomerNotification> notifications(String customerId) {
        return database.execute("notifications.by_customer", true, () -> template.find(
                        Query.query(Criteria.where("customerId").is(customerId))
                                .with(Sort.by(Sort.Direction.DESC, "createdAt")),
                        CustomerNotificationDocument.class).stream()
                .map(CustomerNotificationDocument::toDomain).toList());
    }

    @Override
    /** Executes the mark read persistence operation against the selected database. */
    public CustomerNotification markRead(String customerId, String notificationId, long expectedVersion, Instant readAt) {
        return database.execute("notifications.mark_read", true, () -> {
            var query = Query.query(new Criteria().andOperator(Criteria.where("_id").is(notificationId),
                    Criteria.where("customerId").is(customerId), Criteria.where("version").is(expectedVersion),
                    Criteria.where("readAt").is(null)));
            var updated = template.findAndModify(query, new Update().set("readAt", readAt).inc("version", 1),
                    FindAndModifyOptions.options().returnNew(true), CustomerNotificationDocument.class);
            if (updated != null) return updated.toDomain();
            var current = template.findOne(Query.query(new Criteria().andOperator(
                    Criteria.where("_id").is(notificationId), Criteria.where("customerId").is(customerId))),
                    CustomerNotificationDocument.class);
            if (current == null) throw new NotFoundException("Unknown notification " + notificationId);
            if (current.readAt != null) return current.toDomain();
            throw new StoreConflictException("Notification changed in another request; refresh and retry");
        });
    }

    @Override
    /** Executes the ready for delivery persistence operation against the selected database. */
    public List<CustomerNotification> readyForDelivery(Instant now, int limit) {
        return database.execute("notifications.delivery.ready", true, () -> template.find(
                        Query.query(new Criteria().andOperator(
                                        Criteria.where("deliveryStatus").is(CustomerNotification.DeliveryStatus.PENDING),
                                        Criteria.where("nextAttemptAt").lte(now)))
                                .with(Sort.by("createdAt")).limit(limit),
                        CustomerNotificationDocument.class).stream()
                .map(CustomerNotificationDocument::toDomain).toList());
    }

    @Override
    /** Executes the mark delivered persistence operation against the selected database. */
    public boolean markDelivered(String notificationId, long expectedVersion, Instant deliveredAt) {
        return database.execute("notifications.delivery.complete", true, () -> template.updateFirst(
                deliveryGuard(notificationId, expectedVersion), new Update()
                        .set("deliveryStatus", CustomerNotification.DeliveryStatus.DELIVERED)
                        .set("deliveredAt", deliveredAt).unset("nextAttemptAt").unset("lastError")
                        .inc("deliveryAttempts", 1).inc("version", 1), CustomerNotificationDocument.class)
                .getModifiedCount() == 1);
    }

    @Override
    /** Executes the record delivery failure persistence operation against the selected database. */
    public boolean recordDeliveryFailure(String notificationId, long expectedVersion, int attempts,
                                         Instant nextAttemptAt, String error) {
        return database.execute("notifications.delivery.retry", true, () -> template.updateFirst(
                deliveryGuard(notificationId, expectedVersion), new Update().set("deliveryAttempts", attempts)
                        .set("nextAttemptAt", nextAttemptAt).set("lastError", error).inc("version", 1),
                CustomerNotificationDocument.class).getModifiedCount() == 1);
    }

    /** Executes the delivery guard persistence operation against the selected database. */
    private static Query deliveryGuard(String id, long version) {
        return Query.query(new Criteria().andOperator(Criteria.where("_id").is(id),
                Criteria.where("version").is(version),
                Criteria.where("deliveryStatus").is(CustomerNotification.DeliveryStatus.PENDING)));
    }
}
