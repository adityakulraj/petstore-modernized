package com.mongodb.modernization.petstore.config;

import com.mongodb.modernization.petstore.observability.RequestTelemetry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private final RequestTelemetry telemetry;

    /** Creates a correlation id filter and wires its required collaborators. */
    public CorrelationIdFilter(RequestTelemetry telemetry) { this.telemetry = telemetry; }

    @Override
    /** Configures or applies do filter internal behavior for the application runtime. */
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Prefer the conventional request header while retaining the challenge's original correlation header.
        var supplied = request.getHeader(REQUEST_ID_HEADER);
        if (supplied == null) supplied = request.getHeader(CORRELATION_ID_HEADER);
        var requestId = supplied != null && SAFE.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, requestId);
        var started = System.nanoTime();
        try (var ignoredRequest = MDC.putCloseable("requestId", requestId);
             var ignoredCorrelation = MDC.putCloseable("correlationId", requestId)) {
            try {
                chain.doFilter(request, response);
            } finally {
                var durationNanos = System.nanoTime() - started;
                telemetry.record(request.getRequestURI(), response.getStatus(), durationNanos);
                // One completion event per request is the stable search anchor in the local JSON log.
                var event = LOG.atInfo()
                        .addKeyValue("event", "http.request.completed")
                        .addKeyValue("httpMethod", request.getMethod())
                        .addKeyValue("path", request.getRequestURI())
                        .addKeyValue("status", response.getStatus())
                        .addKeyValue("durationMs", durationNanos / 1_000_000);
                if (request.getUserPrincipal() != null) {
                    event = event.addKeyValue("principal", request.getUserPrincipal().getName());
                }
                event.log("HTTP request completed");
            }
        }
    }
}
