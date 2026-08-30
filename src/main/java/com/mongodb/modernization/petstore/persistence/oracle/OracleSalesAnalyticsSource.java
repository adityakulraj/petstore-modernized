package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.analytics.application.SalesAnalyticsSource;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.domain.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@Profile("oracle")
class OracleSalesAnalyticsSource implements SalesAnalyticsSource {
    private final JpaOrderRepository orders;
    private final DatabaseExecutor database;

    /** Creates the Oracle analytics source backed by the order repository and database executor. */
    OracleSalesAnalyticsSource(JpaOrderRepository orders, DatabaseExecutor database) {
        this.orders = orders;
        this.database = database;
    }

    @Override
    /** Executes the orders between persistence operation against the selected database. */
    public List<Order> ordersBetween(Instant fromInclusive, Instant toExclusive) {
        return database.execute("admin.analytics.sales", true, () -> orders
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(fromInclusive, toExclusive)
                .stream().map(OrderJpaEntity::toDomain).toList());
    }
}
