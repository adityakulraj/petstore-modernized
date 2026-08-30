package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.orders.domain.Order;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PS_ORDER",
        uniqueConstraints = @UniqueConstraint(name = "UK_PS_ORDER_IDEMPOTENCY", columnNames = {"CUSTOMER_ID", "IDEMPOTENCY_KEY"}),
        indexes = {
                @Index(name = "IX_PS_ORDER_CUSTOMER_CREATED", columnList = "CUSTOMER_ID,CREATED_AT"),
                @Index(name = "IX_PS_ORDER_ANALYTICS_CREATED", columnList = "CREATED_AT")
        })
class OrderJpaEntity {
    @Id @Column(length = 36) String id;
    @Column(name = "CUSTOMER_ID", nullable = false, length = 100) String customerId;
    @Column(name = "IDEMPOTENCY_KEY", nullable = false, length = 100) String idempotencyKey;
    @Column(name = "CREATED_AT", nullable = false) Instant createdAt;
    @Column(nullable = false, length = 30) String status;
    @Embedded AddressJpa shippingAddress;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "PS_ORDER_LINE", joinColumns = @JoinColumn(name = "ORDER_ID"),
            indexes = @Index(name = "IX_PS_ORDER_LINE_ORDER", columnList = "ORDER_ID"))
    @OrderColumn(name = "LINE_NUMBER")
    List<OrderLineJpa> lines = new ArrayList<>();
    @Column(nullable = false, precision = 12, scale = 2) BigDecimal total;
    @Version long version;
    @Column(name = "REVIEWED_AT") Instant reviewedAt;
    @Column(name = "REVIEWED_BY", length = 100) String reviewedBy;

    protected OrderJpaEntity() {}
    OrderJpaEntity(Order order) {
        id = order.id(); customerId = order.customerId(); idempotencyKey = order.idempotencyKey();
        createdAt = order.createdAt(); status = order.status(); shippingAddress = new AddressJpa(order.shippingAddress());
        order.lines().stream().map(OrderLineJpa::new).forEach(lines::add); total = order.total();
        reviewedAt = order.reviewedAt(); reviewedBy = order.reviewedBy();
    }
    Order toDomain() {
        return new Order(id, customerId, idempotencyKey, createdAt, status, shippingAddress.toDomain(),
                lines.stream().map(OrderLineJpa::toDomain).toList(), total, version, reviewedAt, reviewedBy);
    }

    void review(String decision, Instant when, String reviewer) {
        status = decision;
        reviewedAt = when;
        reviewedBy = reviewer;
    }

    void allocateInventory(String nextStatus) { status = nextStatus; }
    void customerTransition(String nextStatus) { status = nextStatus; }
}
