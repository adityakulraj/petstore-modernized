package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.application.AdminOrderStore;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.notifications.application.CustomerNotificationStore;
import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.payments.application.PaymentStore;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Repository
@Profile("mongo")
class MongoAdminOrderStore implements AdminOrderStore {
    private final MongoOrderRepository orders;
    private final MongoTemplate template;
    private final TransactionTemplate transactions;
    private final DatabaseExecutor database;
    private final CustomerNotificationStore notifications;
    private final PaymentStore payments;
    private final Clock clock = Clock.systemUTC();

    MongoAdminOrderStore(MongoOrderRepository orders, MongoTemplate template,
                         @Qualifier("mongoTransactionManager") PlatformTransactionManager transactionManager,
                         DatabaseExecutor database, CustomerNotificationStore notifications, PaymentStore payments) {
        this.orders = orders;
        this.template = template;
        this.transactions = new TransactionTemplate(transactionManager);
        this.database = database;
        this.notifications = notifications;
        this.payments = payments;
    }

    @Override
    public List<Order> orders() {
        return database.execute("admin.orders.all", true, () -> orders.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderDocument::toDomain).toList());
    }

    @Override
    public Order review(String orderId, long expectedVersion, Decision decision, String reviewer) {
        return database.execute(decision == Decision.APPROVED ? "admin.order.approve" : "admin.order.deny", true,
                () -> transactions.execute(ignored -> reviewOnce(orderId, expectedVersion, decision, reviewer)));
    }

    private Order reviewOnce(String orderId, long expectedVersion, Decision decision, String reviewer) {
        var current = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Unknown order " + orderId));
        if (compatible(current.status, decision)) {
            var replay = current.toDomain();
            enqueueDecision(replay, decision);
            return replay;
        }
        if (!Order.PENDING.equals(current.status)) {
            throw new StoreConflictException("Only pending orders can be approved or denied");
        }
        long actualVersion = current.version == null ? 0 : current.version;
        if (actualVersion != expectedVersion) {
            throw new StoreConflictException("Order changed in another request; refresh and retry");
        }

        Criteria version = expectedVersion == 0
                ? new Criteria().orOperator(Criteria.where("version").is(0), Criteria.where("version").exists(false),
                        Criteria.where("version").is(null))
                : Criteria.where("version").is(expectedVersion);
        var query = Query.query(new Criteria().andOperator(Criteria.where("_id").is(orderId),
                Criteria.where("status").is(Order.PENDING), version));
        var reviewedAt = Instant.now(clock);
        var update = new Update().set("status", decision.name()).set("reviewedAt", reviewedAt)
                .set("reviewedBy", reviewer).inc("version", 1);
        if (template.updateFirst(query, update, OrderDocument.class).getModifiedCount() != 1) {
            var winner = orders.findById(orderId).orElseThrow();
            if (compatible(winner.status, decision)) {
                var replay = winner.toDomain();
                enqueueDecision(replay, decision);
                return replay;
            }
            throw new StoreConflictException("Order changed in another request; refresh and retry");
        }
        if (decision == Decision.DENIED) {
            for (var line : current.lines) {
                var restored = template.updateFirst(Query.query(Criteria.where("_id").is(line.productId)),
                        new Update().inc("stock", line.quantity).inc("version", 1), ProductDocument.class);
                if (restored.getModifiedCount() != 1) throw new NotFoundException("Unknown product " + line.productId);
            }
        }
        var reviewed = orders.findById(orderId).orElseThrow().toDomain();
        if (decision == Decision.DENIED) {
            var occurredAt = reviewed.reviewedAt() == null ? Instant.now(clock) : reviewed.reviewedAt();
            payments.voidAuthorization(reviewed, occurredAt);
            notifications.enqueue(reviewed, CustomerNotification.Type.PAYMENT_VOIDED, occurredAt);
        }
        enqueueDecision(reviewed, decision);
        return reviewed;
    }

    private void enqueueDecision(Order order, Decision decision) {
        var type = decision == Decision.APPROVED
                ? CustomerNotification.Type.ORDER_APPROVED : CustomerNotification.Type.ORDER_DENIED;
        var occurredAt = order.reviewedAt() == null ? order.createdAt() : order.reviewedAt();
        notifications.enqueue(order, type, decision == Decision.DENIED ? occurredAt.plusMillis(1) : occurredAt);
    }

    private static boolean compatible(String status, Decision decision) {
        return decision == Decision.APPROVED
                ? Order.APPROVED.equals(status) || Order.COMPLETED.equals(status)
                : Order.DENIED.equals(status);
    }
}
