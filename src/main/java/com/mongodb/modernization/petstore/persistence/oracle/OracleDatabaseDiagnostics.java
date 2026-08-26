package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.observability.DatabaseDiagnostics;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Profile("oracle")
class OracleDatabaseDiagnostics implements DatabaseDiagnostics {
    private static final Duration CACHE_FOR = Duration.ofMinutes(1);
    private final JdbcTemplate jdbc;
    private final HikariDataSource dataSource;
    private volatile QueryPlanReport cached;

    OracleDatabaseDiagnostics(JdbcTemplate jdbc, DataSource dataSource) throws Exception {
        this.jdbc = jdbc;
        this.dataSource = dataSource.unwrap(HikariDataSource.class);
    }

    @Override public PoolSnapshot pool() {
        var metrics = dataSource.getHikariPoolMXBean();
        if (metrics == null) {
            return new PoolSnapshot("HikariCP", dataSource.getMinimumIdle(), dataSource.getMaximumPoolSize(),
                    0, 0, 0, 0, 0, 0);
        }
        return new PoolSnapshot("HikariCP", dataSource.getMinimumIdle(), dataSource.getMaximumPoolSize(),
                metrics.getTotalConnections(), metrics.getActiveConnections(), metrics.getIdleConnections(),
                metrics.getThreadsAwaitingConnection(), 0, 0);
    }

    @Override public QueryPlanReport queryPlans() {
        var current = cached;
        if (current != null && current.capturedAt().plus(CACHE_FOR).isAfter(Instant.now())) return current;
        synchronized (this) {
            current = cached;
            if (current != null && current.capturedAt().plus(CACHE_FOR).isAfter(Instant.now())) return current;
            var plans = List.of(
                    explain("catalog.products.all", "SELECT * FROM PS_PRODUCT"),
                    explain("catalog.product.by_id", "SELECT * FROM PS_PRODUCT WHERE ID = 'AV-CB-01'"),
                    explain("catalog.products.by_category", "SELECT * FROM PS_PRODUCT WHERE CATEGORY_ID = 'FISH'"),
                    explain("cart.by_customer", "SELECT * FROM PS_CART WHERE CUSTOMER_ID = 'alice'"),
                    explain("cart.lines.by_customer", "SELECT * FROM PS_CART_LINE WHERE CUSTOMER_ID = 'alice'"),
                    explain("orders.by_idempotency", "SELECT * FROM PS_ORDER WHERE CUSTOMER_ID = 'alice' AND IDEMPOTENCY_KEY = 'diagnostic'"),
                    explain("orders.by_customer", "SELECT * FROM PS_ORDER WHERE CUSTOMER_ID = 'alice' ORDER BY CREATED_AT DESC"),
                    explain("orders.lines.by_order", "SELECT * FROM PS_ORDER_LINE WHERE ORDER_ID = 'diagnostic'"),
                    explain("admin.analytics.sales", "SELECT * FROM PS_ORDER WHERE CREATED_AT >= TIMESTAMP '2026-08-01 00:00:00' AND CREATED_AT < TIMESTAMP '2026-09-01 00:00:00' ORDER BY CREATED_AT"),
                    explain("inventory.conditional_decrement", "SELECT * FROM PS_PRODUCT WHERE ID = 'AV-CB-01' AND STOCK >= 1"));
            cached = new QueryPlanReport(Instant.now(), plans);
            return cached;
        }
    }

    private QueryPlanSnapshot explain(String operation, String sql) {
        String statementId = "PS" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        try {
            jdbc.execute("EXPLAIN PLAN SET STATEMENT_ID = '" + statementId + "' FOR " + sql);
            var rows = jdbc.query("SELECT OPERATION, OPTIONS, OBJECT_NAME, COST, CARDINALITY " +
                            "FROM PLAN_TABLE WHERE STATEMENT_ID = ? ORDER BY ID", (rs, row) ->
                            new PlanRow(rs.getString("OPERATION"), rs.getString("OPTIONS"), rs.getString("OBJECT_NAME"),
                                    rs.getLong("COST"), rs.getLong("CARDINALITY")), statementId);
            var index = rows.stream().filter(row -> row.operation().contains("INDEX")).findFirst();
            var fullScan = rows.stream().anyMatch(row -> row.operation().equals("TABLE ACCESS")
                    && row.options().contains("FULL"));
            var root = rows.isEmpty() ? new PlanRow("UNKNOWN", "", "", 0, 0) : rows.getFirst();
            return new QueryPlanSnapshot(operation, sql, index.isPresent() ? "IXSCAN" : fullScan ? "COLLSCAN" : "OTHER",
                    index.map(PlanRow::objectName).orElse(""), 0, 0, root.cardinality(), 0,
                    "Oracle optimizer estimate: cost=" + root.cost() + "; cached for 60 seconds");
        } catch (RuntimeException failure) {
            return new QueryPlanSnapshot(operation, sql, "UNAVAILABLE", "", 0, 0, 0, 0,
                    failure.getClass().getSimpleName() + ": " + (failure.getMessage() == null ? "" : failure.getMessage()));
        } finally {
            try { jdbc.update("DELETE FROM PLAN_TABLE WHERE STATEMENT_ID = ?", statementId); }
            catch (RuntimeException ignored) { /* Diagnostic cleanup must not affect application health. */ }
        }
    }

    private record PlanRow(String operation, String options, String objectName, long cost, long cardinality) {
        private PlanRow {
            operation = operation == null ? "" : operation;
            options = options == null ? "" : options;
            objectName = objectName == null ? "" : objectName;
        }
    }
}
