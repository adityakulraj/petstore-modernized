package com.mongodb.modernization.petstore.supplier.domain;

import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.orders.domain.OrderLine;

import java.time.Instant;
import java.util.List;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;

/** Durable, versioned supplier hand-off derived from an approved customer order. */
public record SupplierPurchaseOrder(String id, String orderId, String customerId, Instant createdAt,
                                    Status status, List<OrderLine> lines, long version, Instant processedAt) {
    public enum Status { READY, PROCESSED, CANCELLED }

    /** Defensively copies purchase-order lines to protect the fulfilment snapshot. */
    public SupplierPurchaseOrder {
        lines = List.copyOf(lines);
    }

    /** Creates the deterministic supplier purchase order for an approved customer order. */
    public static SupplierPurchaseOrder ready(Order order) {
        return new SupplierPurchaseOrder(order.id(), order.id(), order.customerId(), order.createdAt(),
                Status.READY, order.lines(), 0, null);
    }

    /** Idempotently marks a ready purchase order processed and rejects cancelled work. */
    public SupplierPurchaseOrder processed(Instant when) {
        if (status == Status.PROCESSED) return this;
        if (status != Status.READY) throw new StoreConflictException("Cancelled purchase orders cannot be processed");
        return new SupplierPurchaseOrder(id, orderId, customerId, createdAt, Status.PROCESSED,
                lines, version, when);
    }
}
