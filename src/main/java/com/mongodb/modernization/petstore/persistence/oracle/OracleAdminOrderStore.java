package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.application.AdminOrderStore;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Repository
@Profile("oracle")
class OracleAdminOrderStore implements AdminOrderStore {
    private final JpaOrderRepository orders;
    private final JpaProductRepository products;
    private final TransactionTemplate transactions;
    private final DatabaseExecutor database;
    private final Clock clock = Clock.systemUTC();

    OracleAdminOrderStore(JpaOrderRepository orders, JpaProductRepository products,
                          PlatformTransactionManager transactionManager, DatabaseExecutor database) {
        this.orders = orders;
        this.products = products;
        this.transactions = new TransactionTemplate(transactionManager);
        this.database = database;
    }

    @Override
    public List<Order> orders() {
        return database.execute("admin.orders.all", true, () -> orders.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderJpaEntity::toDomain).toList());
    }

    @Override
    public Order review(String orderId, long expectedVersion, Decision decision, String reviewer) {
        return database.execute(decision == Decision.APPROVED ? "admin.order.approve" : "admin.order.deny", true,
                () -> transactions.execute(ignored -> {
                    var entity = orders.findByIdForReview(orderId)
                            .orElseThrow(() -> new NotFoundException("Unknown order " + orderId));
                    if (compatible(entity.status, decision)) return entity.toDomain();
                    if (!Order.PENDING.equals(entity.status)) {
                        throw new StoreConflictException("Only pending orders can be approved or denied");
                    }
                    if (entity.version != expectedVersion) {
                        throw new StoreConflictException("Order changed in another request; refresh and retry");
                    }
                    entity.review(decision.name(), Instant.now(clock), reviewer);
                    if (decision == Decision.DENIED) {
                        for (var line : entity.lines) {
                            if (products.restoreStock(line.productId, line.quantity) != 1) {
                                throw new NotFoundException("Unknown product " + line.productId);
                            }
                        }
                    }
                    return orders.saveAndFlush(entity).toDomain();
                }));
    }

    private static boolean compatible(String status, Decision decision) {
        return decision == Decision.APPROVED
                ? Order.APPROVED.equals(status) || Order.COMPLETED.equals(status)
                : Order.DENIED.equals(status);
    }
}
