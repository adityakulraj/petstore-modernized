package com.mongodb.modernization.petstore.analytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SalesAnalytics(LocalDate from, LocalDate to, String categoryId, String dimension,
                             Instant generatedAt, Summary summary, List<CategoryOption> categories,
                             List<Breakdown> breakdown, List<DailyPoint> daily,
                             List<StatusPoint> statuses) {
    public SalesAnalytics {
        categories = List.copyOf(categories);
        breakdown = List.copyOf(breakdown);
        daily = List.copyOf(daily);
        statuses = List.copyOf(statuses);
    }

    public record Summary(long acceptedOrders, long unitsSold, BigDecimal revenue,
                          BigDecimal averageOrderValue, BigDecimal pendingValue) {}
    public record CategoryOption(String id, String name) {}
    public record Breakdown(String key, String label, long orderCount, long unitsSold,
                            BigDecimal revenue) {}
    public record DailyPoint(LocalDate date, long orderCount, long unitsSold, BigDecimal revenue) {}
    public record StatusPoint(String status, long orderCount, BigDecimal value) {}
}
