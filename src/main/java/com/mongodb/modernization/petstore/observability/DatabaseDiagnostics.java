package com.mongodb.modernization.petstore.observability;

import java.time.Instant;
import java.util.List;

public interface DatabaseDiagnostics {
    PoolSnapshot pool();
    QueryPlanReport queryPlans();

    record PoolSnapshot(String provider, int configuredMin, int configuredMax, int total,
                        int active, int idle, int awaiting, long acquisitionFailures,
                        double averageAcquireMs) {}

    record QueryPlanReport(Instant capturedAt, List<QueryPlanSnapshot> plans) {}

    record QueryPlanSnapshot(String operation, String query, String scanType, String indexName,
                             long documentsExamined, long keysExamined, long rowsReturned,
                             long executionTimeMs, String detail) {}
}
