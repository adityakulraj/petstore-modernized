package com.mongodb.modernization.petstore.analytics.api;

import com.mongodb.modernization.petstore.analytics.application.SalesAnalyticsService;
import com.mongodb.modernization.petstore.analytics.domain.SalesAnalytics;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class SalesAnalyticsController {
    private final SalesAnalyticsService analytics;

    /** Creates a sales analytics controller and wires its required collaborators. */
    public SalesAnalyticsController(SalesAnalyticsService analytics) { this.analytics = analytics; }

    @GetMapping("/sales")
    /** Handles the sales HTTP request and returns its API response. */
    public SalesAnalytics sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String category) {
        var today = LocalDate.now(ZoneOffset.UTC);
        return analytics.report(from == null ? today.minusDays(29) : from, to == null ? today : to, category);
    }
}
