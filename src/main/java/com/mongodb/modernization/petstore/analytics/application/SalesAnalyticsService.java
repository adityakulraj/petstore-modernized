package com.mongodb.modernization.petstore.analytics.application;

import com.mongodb.modernization.petstore.analytics.domain.SalesAnalytics;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.orders.domain.OrderLine;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StorefrontStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SalesAnalyticsService {
    private static final Logger LOG = LoggerFactory.getLogger(SalesAnalyticsService.class);
    private static final Set<String> RECOGNIZED = Set.of(Order.APPROVED, Order.COMPLETED);
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final SalesAnalyticsSource source;
    private final StorefrontStore storefront;
    private final Clock clock;

    @Autowired
    public SalesAnalyticsService(SalesAnalyticsSource source, StorefrontStore storefront) {
        this(source, storefront, Clock.systemUTC());
    }

    SalesAnalyticsService(SalesAnalyticsSource source, StorefrontStore storefront, Clock clock) {
        this.source = source;
        this.storefront = storefront;
        this.clock = clock;
    }

    public SalesAnalytics report(LocalDate from, LocalDate to, String requestedCategory) {
        validateRange(from, to);
        var products = storefront.products();
        var productById = products.stream().collect(java.util.stream.Collectors.toMap(Product::id, product -> product));
        var categories = products.stream()
                .map(product -> new SalesAnalytics.CategoryOption(product.categoryId(), product.categoryName()))
                .distinct().sorted(Comparator.comparing(SalesAnalytics.CategoryOption::name)).toList();
        var categoryId = normalizeCategory(requestedCategory);
        if (categoryId != null && categories.stream().noneMatch(category -> category.id().equals(categoryId))) {
            throw new NotFoundException("Unknown category " + categoryId);
        }

        var orders = source.ordersBetween(from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        var acceptedOrderIds = new LinkedHashSet<String>();
        var breakdown = new HashMap<String, MutableAggregate>();
        var daily = new LinkedHashMap<LocalDate, MutableAggregate>();
        from.datesUntil(to.plusDays(1))
                .forEach(date -> daily.put(date, new MutableAggregate(date.toString(), date.toString())));
        var statuses = new HashMap<String, MutableAggregate>();
        long acceptedUnits = 0;
        var acceptedRevenue = ZERO;
        var pendingValue = ZERO;

        for (var order : orders) {
            var matching = matchingLines(order.lines(), productById, categoryId);
            if (matching.isEmpty()) continue;
            var matchingValue = matching.stream().map(LineWithProduct::line).map(OrderLine::subtotal)
                    .reduce(ZERO, BigDecimal::add);
            var matchingUnits = matching.stream().map(LineWithProduct::line)
                    .mapToLong(OrderLine::quantity).sum();
            statuses.computeIfAbsent(order.status(), key -> new MutableAggregate(key, statusLabel(key)))
                    .addOrder(order.id(), matchingUnits, matchingValue);
            if (Order.PENDING.equals(order.status())) pendingValue = pendingValue.add(matchingValue);
            if (!RECOGNIZED.contains(order.status())) continue;

            acceptedOrderIds.add(order.id());
            var day = order.createdAt().atZone(ZoneOffset.UTC).toLocalDate();
            var dayAggregate = daily.get(day);
            for (var entry : matching) {
                var line = entry.line();
                var product = entry.product();
                acceptedUnits += line.quantity();
                acceptedRevenue = acceptedRevenue.add(line.subtotal());
                var key = categoryId == null ? product.categoryId() : product.id();
                var label = categoryId == null ? product.categoryName() : product.displayName();
                breakdown.computeIfAbsent(key, ignored -> new MutableAggregate(key, label))
                        .addOrder(order.id(), line.quantity(), line.subtotal());
                if (dayAggregate != null) dayAggregate.addOrder(order.id(), line.quantity(), line.subtotal());
            }
        }

        var average = acceptedOrderIds.isEmpty() ? ZERO : acceptedRevenue.divide(
                BigDecimal.valueOf(acceptedOrderIds.size()), 2, RoundingMode.HALF_EVEN);
        var response = new SalesAnalytics(from, to, categoryId, categoryId == null ? "CATEGORY" : "ITEM",
                Instant.now(clock), new SalesAnalytics.Summary(acceptedOrderIds.size(), acceptedUnits,
                money(acceptedRevenue), money(average), money(pendingValue)), categories,
                breakdown.values().stream().sorted(Comparator.comparing(MutableAggregate::revenue).reversed()
                                .thenComparing(MutableAggregate::label)).map(MutableAggregate::breakdown).toList(),
                daily.values().stream().map(MutableAggregate::daily).toList(),
                statuses.values().stream().sorted(Comparator.comparingInt(value -> statusOrder(value.key())))
                        .map(MutableAggregate::status).toList());
        LOG.atInfo().addKeyValue("event", "admin.sales_analytics.viewed")
                .addKeyValue("from", from).addKeyValue("to", to)
                .addKeyValue("categoryId", categoryId == null ? "ALL" : categoryId)
                .addKeyValue("acceptedOrders", response.summary().acceptedOrders())
                .addKeyValue("revenue", response.summary().revenue())
                .log("Administrator sales analytics generated");
        return response;
    }

    private static List<LineWithProduct> matchingLines(List<OrderLine> lines, Map<String, Product> products,
                                                       String categoryId) {
        var result = new ArrayList<LineWithProduct>();
        for (var line : lines) {
            var product = products.get(line.productId());
            if (product != null && (categoryId == null || categoryId.equals(product.categoryId()))) {
                result.add(new LineWithProduct(line, product));
            }
        }
        return result;
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new InvalidAnalyticsRangeException("Both from and to dates are required");
        if (from.isAfter(to)) throw new InvalidAnalyticsRangeException("from must be on or before to");
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new InvalidAnalyticsRangeException("Date range cannot exceed 367 days");
        }
    }

    private static String normalizeCategory(String category) {
        return category == null || category.isBlank() ? null : category.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_EVEN); }
    private static String statusLabel(String status) { return status.replace('_', ' '); }
    private static int statusOrder(String status) {
        return switch (status) {
            case Order.COMPLETED -> 0;
            case Order.APPROVED -> 1;
            case Order.PENDING -> 2;
            case Order.BACKORDERED -> 3;
            case Order.DENIED -> 4;
            case Order.CANCELLED -> 5;
            case Order.REFUNDED -> 6;
            default -> 7;
        };
    }

    private record LineWithProduct(OrderLine line, Product product) {}

    private static final class MutableAggregate {
        private final String key;
        private final String label;
        private final Set<String> orderIds = new LinkedHashSet<>();
        private long units;
        private BigDecimal revenue = ZERO;

        private MutableAggregate(String key, String label) { this.key = key; this.label = label; }
        private void addOrder(String orderId, long quantity, BigDecimal value) {
            orderIds.add(orderId); units += quantity; revenue = revenue.add(value);
        }
        private String key() { return key; }
        private String label() { return label; }
        private BigDecimal revenue() { return revenue; }
        private SalesAnalytics.Breakdown breakdown() {
            return new SalesAnalytics.Breakdown(key, label, orderIds.size(), units, money(revenue));
        }
        private SalesAnalytics.DailyPoint daily() {
            return new SalesAnalytics.DailyPoint(LocalDate.parse(key), orderIds.size(), units, money(revenue));
        }
        private SalesAnalytics.StatusPoint status() {
            return new SalesAnalytics.StatusPoint(key, orderIds.size(), money(revenue));
        }
    }
}
