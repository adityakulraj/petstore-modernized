package com.mongodb.modernization.petstore.shared.api;

import com.mongodb.modernization.petstore.config.AppProperties;
import com.mongodb.modernization.petstore.observability.DatabaseDiagnostics;
import com.mongodb.modernization.petstore.observability.DatabaseTelemetry;
import com.mongodb.modernization.petstore.observability.RequestTelemetry;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/health")
public class AdminHealthController {
    private final AppProperties properties;
    private final RequestTelemetry requests;
    private final DatabaseTelemetry databaseTelemetry;
    private final DatabaseDiagnostics databaseDiagnostics;
    private final HealthEndpoint health;

    /** Creates a admin health controller and wires its required collaborators. */
    public AdminHealthController(AppProperties properties, RequestTelemetry requests,
                                 DatabaseTelemetry databaseTelemetry, DatabaseDiagnostics databaseDiagnostics,
                                 HealthEndpoint health) {
        this.properties = properties;
        this.requests = requests;
        this.databaseTelemetry = databaseTelemetry;
        this.databaseDiagnostics = databaseDiagnostics;
        this.health = health;
    }

    @GetMapping
    /** Handles the dashboard HTTP request and returns its API response. */
    public DashboardResponse dashboard() {
        var runtime = ManagementFactory.getRuntimeMXBean();
        var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var threads = ManagementFactory.getThreadMXBean();
        var traffic = requests.snapshot();
        var points = traffic.series().stream().map(point -> new TrafficPoint(point.timestamp(), point.requests(),
                point.clientErrors(), point.serverErrors(), point.averageLatencyMs(),
                point.maxNanos() / 1_000_000.0)).toList();
        var last = points.getLast();
        return new DashboardResponse(health.health().getStatus().getCode(),
                properties.store().name().toLowerCase(), Instant.ofEpochMilli(runtime.getStartTime()),
                runtime.getUptime() / 1000,
                new TrafficSummary(traffic.lifetimeRequests(), traffic.lifetimeClientErrors(),
                        traffic.lifetimeServerErrors(), traffic.windowRequests(), traffic.windowClientErrors(),
                        traffic.windowServerErrors(), traffic.clientErrorRatePercent(),
                        traffic.serverErrorRatePercent(), last.requests(), traffic.averageLatencyMs(),
                        traffic.maxLatencyMs(), points),
                new JvmSummary(memory.getUsed(), memory.getCommitted(), memory.getMax(), threads.getThreadCount(),
                        threads.getPeakThreadCount(), Runtime.getRuntime().availableProcessors()),
                new DatabaseSummary(databaseDiagnostics.pool(), databaseTelemetry.snapshot(),
                        databaseDiagnostics.queryPlans()));
    }

    /** Handles the dashboard response HTTP request and returns its API response. */
    public record DashboardResponse(String status, String store, Instant startedAt, long uptimeSeconds,
                                    TrafficSummary traffic, JvmSummary jvm, DatabaseSummary database) {}

    /** Handles the traffic summary HTTP request and returns its API response. */
    public record TrafficSummary(long lifetimeRequests, long lifetimeClientErrors, long lifetimeServerErrors,
                                 long windowRequests, long windowClientErrors, long windowServerErrors,
                                 double clientErrorRatePercent, double serverErrorRatePercent,
                                 long requestsThisMinute, double averageLatencyMs, double maxLatencyMs,
                                 List<TrafficPoint> series) {}

    /** Handles the traffic point HTTP request and returns its API response. */
    public record TrafficPoint(Instant timestamp, long requests, long clientErrors, long serverErrors,
                               double averageLatencyMs, double maxLatencyMs) {}

    /** Handles the jvm summary HTTP request and returns its API response. */
    public record JvmSummary(long heapUsedBytes, long heapCommittedBytes, long heapMaxBytes,
                             int liveThreads, int peakThreads, int processors) {}

    /** Handles the database summary HTTP request and returns its API response. */
    public record DatabaseSummary(DatabaseDiagnostics.PoolSnapshot pool,
                                  List<DatabaseTelemetry.OperationSnapshot> operations,
                                  DatabaseDiagnostics.QueryPlanReport queryPlans) {}
}
