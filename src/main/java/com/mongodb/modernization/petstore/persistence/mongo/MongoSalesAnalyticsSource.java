package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.analytics.application.SalesAnalyticsSource;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.orders.domain.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@Profile("mongo")
class MongoSalesAnalyticsSource implements SalesAnalyticsSource {
    private final MongoOrderRepository orders;
    private final DatabaseExecutor database;

    MongoSalesAnalyticsSource(MongoOrderRepository orders, DatabaseExecutor database) {
        this.orders = orders;
        this.database = database;
    }

    @Override
    public List<Order> ordersBetween(Instant fromInclusive, Instant toExclusive) {
        return database.execute("admin.analytics.sales", true, () -> orders
                .findForSalesAnalytics(fromInclusive, toExclusive)
                .stream().map(OrderDocument::toDomain).toList());
    }
}
