package com.mongodb.modernization.petstore.analytics.application;

import com.mongodb.modernization.petstore.orders.domain.Order;

import java.time.Instant;
import java.util.List;

public interface SalesAnalyticsSource {
    List<Order> ordersBetween(Instant fromInclusive, Instant toExclusive);
}
