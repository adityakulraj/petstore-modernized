package com.mongodb.modernization.petstore.supplier.domain;

import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.orders.domain.OrderLine;

import java.time.Instant;
import java.util.List;

public record SupplierPurchaseOrder(String id, String orderId, String customerId, Instant createdAt,
                                    Status status, List<OrderLine> lines, long version, Instant processedAt) {
    public enum Status { READY, PROCESSED }

    public SupplierPurchaseOrder {
        lines = List.copyOf(lines);
    }

    public static SupplierPurchaseOrder ready(Order order) {
        return new SupplierPurchaseOrder(order.id(), order.id(), order.customerId(), order.createdAt(),
                Status.READY, order.lines(), 0, null);
    }

    public SupplierPurchaseOrder processed(Instant when) {
        if (status == Status.PROCESSED) return this;
        return new SupplierPurchaseOrder(id, orderId, customerId, createdAt, Status.PROCESSED,
                lines, version, when);
    }
}
