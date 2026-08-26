package com.mongodb.modernization.petstore.analytics.application;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.application.SeedProducts;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.orders.domain.OrderLine;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StorefrontStore;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesAnalyticsServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");
    private static final Address ADDRESS = new Address("Alice", "1 Main", "", "Pune", "MH", "411001", "India");

    @Test
    void separatesRecognizedRevenueFromPipelineAndDrillsIntoItems() {
        var orders = List.of(
                order("completed", Order.COMPLETED, "2026-08-25T10:00:00Z",
                        line("FI-SW-01", "Angelfish", "16.50", 2), line("AV-CB-01", "Canary", "125.00", 1)),
                order("approved", Order.APPROVED, "2026-08-26T10:00:00Z",
                        line("K9-BD-01", "Male Adult Bulldog", "850.00", 1)),
                order("pending", Order.PENDING, "2026-08-26T11:00:00Z",
                        line("K9-RT-01", "Golden Retriever", "950.00", 1)),
                order("denied", Order.DENIED, "2026-08-26T11:15:00Z",
                        line("FL-DSH-01", "Domestic Shorthair", "320.00", 1)));
        var service = service(orders);

        var all = service.report(LocalDate.parse("2026-08-25"), LocalDate.parse("2026-08-26"), null);

        assertThat(all.summary().acceptedOrders()).isEqualTo(2);
        assertThat(all.summary().unitsSold()).isEqualTo(4);
        assertThat(all.summary().revenue()).isEqualByComparingTo("1008.00");
        assertThat(all.summary().averageOrderValue()).isEqualByComparingTo("504.00");
        assertThat(all.summary().pendingValue()).isEqualByComparingTo("950.00");
        assertThat(all.breakdown()).extracting(item -> item.key()).containsExactly("DOGS", "BIRDS", "FISH");
        assertThat(all.daily()).extracting(item -> item.revenue())
                .containsExactly(new BigDecimal("158.00"), new BigDecimal("850.00"));
        assertThat(all.statuses()).extracting(item -> item.status())
                .containsExactly(Order.COMPLETED, Order.APPROVED, Order.PENDING, Order.DENIED);

        var dogs = service.report(LocalDate.parse("2026-08-25"), LocalDate.parse("2026-08-26"), " dogs ");
        assertThat(dogs.dimension()).isEqualTo("ITEM");
        assertThat(dogs.summary().revenue()).isEqualByComparingTo("850.00");
        assertThat(dogs.summary().pendingValue()).isEqualByComparingTo("950.00");
        assertThat(dogs.breakdown()).singleElement().satisfies(item -> {
            assertThat(item.key()).isEqualTo("K9-BD-01");
            assertThat(item.label()).isEqualTo("Male Adult Bulldog");
        });
    }

    @Test
    void rejectsInvalidRangesAndUnknownCategories() {
        var service = service(List.of());
        assertThatThrownBy(() -> service.report(LocalDate.parse("2026-08-27"), LocalDate.parse("2026-08-26"), null))
                .isInstanceOf(InvalidAnalyticsRangeException.class).hasMessageContaining("from");
        assertThatThrownBy(() -> service.report(LocalDate.parse("2024-01-01"), LocalDate.parse("2026-08-26"), null))
                .isInstanceOf(InvalidAnalyticsRangeException.class).hasMessageContaining("exceed");
        assertThatThrownBy(() -> service.report(LocalDate.parse("2026-08-26"), LocalDate.parse("2026-08-26"), "horses"))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("HORSES");
    }

    private static SalesAnalyticsService service(List<Order> orders) {
        return new SalesAnalyticsService((from, to) -> orders.stream()
                .filter(order -> !order.createdAt().isBefore(from) && order.createdAt().isBefore(to)).toList(),
                new CatalogOnlyStore(SeedProducts.all()), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Order order(String id, String status, String createdAt, OrderLine... lines) {
        var total = java.util.Arrays.stream(lines).map(OrderLine::subtotal)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        return new Order(id, "alice", id, Instant.parse(createdAt), status, ADDRESS,
                List.of(lines), total, 0, null, null);
    }

    private static OrderLine line(String id, String name, String price, int quantity) {
        var unitPrice = new BigDecimal(price);
        return new OrderLine(id, name, unitPrice, quantity, unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    private record CatalogOnlyStore(List<Product> catalog) implements StorefrontStore {
        @Override public List<Product> products(String categoryId) { return catalog; }
        @Override public Optional<Product> product(String productId) { return catalog.stream().filter(p -> p.id().equals(productId)).findFirst(); }
        @Override public Cart cart(String customerId) { throw unsupported(); }
        @Override public Cart addToCart(String customerId, long version, String productId, int quantity) { throw unsupported(); }
        @Override public Cart updateCart(String customerId, long version, String productId, int quantity) { throw unsupported(); }
        @Override public Cart removeFromCart(String customerId, long version, String productId) { throw unsupported(); }
        @Override public Order checkout(String customerId, long version, String key, Address address) { throw unsupported(); }
        @Override public Optional<Order> orderByIdempotencyKey(String customerId, String key) { throw unsupported(); }
        @Override public List<Order> orders(String customerId) { throw unsupported(); }
        @Override public void seedIfEmpty() { throw unsupported(); }
    }

    private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
}
