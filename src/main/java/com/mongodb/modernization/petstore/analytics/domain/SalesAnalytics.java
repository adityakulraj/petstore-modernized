package com.mongodb.modernization.petstore.analytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Immutable, filter-aware sales report returned to the administrator dashboard. */
public record SalesAnalytics(LocalDate from, LocalDate to, String categoryId, String dimension,
                             Instant generatedAt, Summary summary, List<CategoryOption> categories,
                             List<Breakdown> breakdown, List<DailyPoint> daily,
                             List<StatusPoint> statuses) {
    /** Defensively copies every result series so a generated report remains immutable. */
    public SalesAnalytics {
        categories = List.copyOf(categories);
        breakdown = List.copyOf(breakdown);
        daily = List.copyOf(daily);
        statuses = List.copyOf(statuses);
    }

    /** Holds top-level recognized revenue, volume, average-value, and pending-pipeline metrics. */
    public record Summary(long acceptedOrders, long unitsSold, BigDecimal revenue,
                          BigDecimal averageOrderValue, BigDecimal pendingValue) {}
    /** Identifies a catalog category available to the analytics filter. */
    public record CategoryOption(String id, String name) {}
    /** Holds one category, product, or item contribution to the selected report. */
    public record Breakdown(String key, String label, long orderCount, long unitsSold,
                            BigDecimal revenue) {}
    /** Holds one date bucket used by the revenue trend graph. */
    public record DailyPoint(LocalDate date, long orderCount, long unitsSold, BigDecimal revenue) {}
    /** Holds the order count and value for one lifecycle status. */
    public record StatusPoint(String status, long orderCount, BigDecimal value) {}
}
