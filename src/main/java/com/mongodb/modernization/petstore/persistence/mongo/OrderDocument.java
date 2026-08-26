package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.orders.domain.Order;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

@Document("orders")
@CompoundIndexes({
        @CompoundIndex(name = "uk_order_idempotency", def = "{'customerId': 1, 'idempotencyKey': 1}", unique = true),
        @CompoundIndex(name = "ix_order_customer_created", def = "{'customerId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "ix_order_analytics_created", def = "{'createdAt': 1}")
})
class OrderDocument {
    @Id String id;
    String customerId;
    String idempotencyKey;
    Instant createdAt;
    String status;
    AddressDocument shippingAddress;
    ArrayList<OrderLineDocument> lines = new ArrayList<>();
    @Field(targetType = FieldType.DECIMAL128) BigDecimal total;
    Long version;
    Instant reviewedAt;
    String reviewedBy;

    OrderDocument() {}
    OrderDocument(Order order) {
        id = order.id(); customerId = order.customerId(); idempotencyKey = order.idempotencyKey();
        createdAt = order.createdAt(); status = order.status(); shippingAddress = new AddressDocument(order.shippingAddress());
        order.lines().stream().map(OrderLineDocument::new).forEach(lines::add); total = order.total();
        version = order.version(); reviewedAt = order.reviewedAt(); reviewedBy = order.reviewedBy();
    }
    Order toDomain() {
        return new Order(id, customerId, idempotencyKey, createdAt, status, shippingAddress.toDomain(),
                lines.stream().map(OrderLineDocument::toDomain).toList(), total,
                version == null ? 0 : version, reviewedAt, reviewedBy);
    }
}
