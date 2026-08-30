package com.mongodb.modernization.petstore.shared.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

@Validated
@RestController
@RequestMapping("/api/v1/admin/logs")
public class AdminLogController {
    private final ObjectMapper objectMapper;
    private final Path logFile;

    /** Creates a admin log controller and wires its required collaborators. */
    public AdminLogController(ObjectMapper objectMapper,
                              @Value("${logging.file.name:logs/petstore.log}") String logFile) {
        this.objectMapper = objectMapper;
        // The deployment controls one fixed path; callers can filter content but cannot select arbitrary files.
        this.logFile = Path.of(logFile).toAbsolutePath().normalize();
    }

    @GetMapping
    /** Writes structured telemetry for s. */
    public LogSearchResponse logs(
            @RequestParam(required = false)
            @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String requestId,
            @RequestParam(defaultValue = "200") @Min(1) @Max(1000) int limit) {
        if (Files.notExists(logFile)) return new LogSearchResponse(requestId, 0, List.of());

        var recent = new ArrayDeque<JsonNode>(limit);
        var matched = new long[]{0};
        try (var lines = Files.lines(logFile, StandardCharsets.UTF_8)) {
            lines.forEach(line -> parse(line).ifPresent(entry -> {
                if (matches(entry, requestId)) {
                    matched[0]++;
                    if (recent.size() == limit) recent.removeFirst();
                    recent.addLast(entry);
                }
            }));
        } catch (IOException | UncheckedIOException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Log file is temporarily unavailable", failure);
        }
        return new LogSearchResponse(requestId, matched[0], List.copyOf(recent));
    }

    /** Handles the matches HTTP request and returns its API response. */
    private boolean matches(JsonNode entry, String requestId) {
        if (requestId == null) return true;
        var loggedRequestId = entry.get("requestId");
        // Startup and framework events legitimately have no request ID, so never coerce a missing node to text.
        return loggedRequestId != null && loggedRequestId.isString()
                && requestId.equals(loggedRequestId.stringValue());
    }

    /** Handles the parse HTTP request and returns its API response. */
    private java.util.Optional<JsonNode> parse(String line) {
        try {
            return java.util.Optional.of(objectMapper.readTree(line));
        } catch (JacksonException ignored) {
            // A partially written or legacy plain-text line should not make the entire search endpoint fail.
            return java.util.Optional.empty();
        }
    }

    /** Handles the log search response HTTP request and returns its API response. */
    public record LogSearchResponse(String requestId, long matched, List<JsonNode> entries) {}
}
