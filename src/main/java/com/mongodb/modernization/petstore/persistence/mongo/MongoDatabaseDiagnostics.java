package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.observability.DatabaseDiagnostics;
import org.bson.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@Profile("mongo")
class MongoDatabaseDiagnostics implements DatabaseDiagnostics {
    private static final Duration CACHE_FOR = Duration.ofMinutes(1);
    private final MongoTemplate template;
    private final MongoPoolMetrics pool;
    private volatile QueryPlanReport cached;

    /** Creates a mongo database diagnostics and wires its required collaborators. */
    MongoDatabaseDiagnostics(MongoTemplate template, MongoPoolMetrics pool) {
        this.template = template;
        this.pool = pool;
    }

    /** Collects or updates observability data for pool. */
    @Override public PoolSnapshot pool() { return pool.snapshot(); }

    /** Collects or updates observability data for query plans. */
    @Override public QueryPlanReport queryPlans() {
        var current = cached;
        if (current != null && current.capturedAt().plus(CACHE_FOR).isAfter(Instant.now())) return current;
        synchronized (this) {
            current = cached;
            if (current != null && current.capturedAt().plus(CACHE_FOR).isAfter(Instant.now())) return current;
            var plans = List.of(
                    explain("catalog.products.all", "products", new Document(), null),
                    explain("catalog.product.by_id", "products", new Document("_id", "AV-CB-01"), null),
                    explain("catalog.products.by_category", "products", new Document("categoryId", "FISH"), null),
                    explain("cart.by_customer", "carts", new Document("_id", "alice"), null),
                    explain("orders.by_idempotency", "orders",
                            new Document("customerId", "alice").append("idempotencyKey", "diagnostic"), null),
                    explain("orders.by_customer", "orders", new Document("customerId", "alice"),
                            new Document("createdAt", -1)),
                    explain("admin.analytics.sales", "orders", new Document("createdAt",
                            new Document("$gte", java.util.Date.from(Instant.parse("2026-08-01T00:00:00Z")))
                                    .append("$lt", java.util.Date.from(Instant.parse("2026-09-01T00:00:00Z")))),
                            new Document("createdAt", 1)),
                    explain("inventory.conditional_decrement", "products",
                            new Document("_id", "AV-CB-01").append("stock", new Document("$gte", 1)), null));
            cached = new QueryPlanReport(Instant.now(), plans);
            return cached;
        }
    }

    /** Collects or updates observability data for explain. */
    private QueryPlanSnapshot explain(String operation, String collection, Document filter, Document sort) {
        var find = new Document("find", collection).append("filter", filter);
        if (sort != null) find.append("sort", sort);
        var command = new Document("explain", find).append("verbosity", "executionStats");
        var query = collection + ".find(" + filter.toJson() + ")" + (sort == null ? "" : ".sort(" + sort.toJson() + ")");
        try {
            var result = template.executeCommand(command);
            var planner = result.get("queryPlanner", Document.class);
            var winning = planner == null ? null : planner.get("winningPlan", Document.class);
            var scan = findScanStage(winning);
            var stats = result.get("executionStats", Document.class);
            String stage = scan == null ? "UNKNOWN" : scan.getString("stage");
            return new QueryPlanSnapshot(operation, query, normalizeStage(stage),
                    scan == null ? "" : value(scan.get("indexName")),
                    number(stats, "totalDocsExamined"), number(stats, "totalKeysExamined"),
                    number(stats, "nReturned"), number(stats, "executionTimeMillis"),
                    "MongoDB executionStats; cached for 60 seconds");
        } catch (RuntimeException failure) {
            return new QueryPlanSnapshot(operation, query, "UNAVAILABLE", "", 0, 0, 0, 0,
                    failure.getClass().getSimpleName() + ": " + value(failure.getMessage()));
        }
    }

    /** Collects or updates observability data for find scan stage. */
    private static Document findScanStage(Object node) {
        if (node instanceof Document document) {
            var stage = document.getString("stage");
            if ((stage != null && stage.contains("IXSCAN")) || "COLLSCAN".equals(stage)) return document;
            for (var value : document.values()) {
                var found = findScanStage(value);
                if (found != null) return found;
            }
        } else if (node instanceof Iterable<?> iterable) {
            for (var value : iterable) {
                var found = findScanStage(value);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Collects or updates observability data for normalize stage. */
    private static String normalizeStage(String stage) {
        return stage == null ? "UNKNOWN" : stage.contains("IXSCAN") ? "IXSCAN" : stage;
    }

    /** Collects or updates observability data for number. */
    private static long number(Document document, String key) {
        if (document == null || !(document.get(key) instanceof Number number)) return 0;
        return number.longValue();
    }

    /** Collects or updates observability data for value. */
    private static String value(Object value) { return value == null ? "" : value.toString(); }
}
