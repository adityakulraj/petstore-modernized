package com.mongodb.modernization.petstore.notifications.domain;

import com.mongodb.modernization.petstore.orders.domain.Order;

import java.time.Instant;

public record CustomerNotification(String id, String customerId, String orderId, Type type,
                                   String title, String message, Instant createdAt,
                                   DeliveryStatus deliveryStatus, int deliveryAttempts,
                                   Instant nextAttemptAt, Instant deliveredAt, String lastError,
                                   Instant readAt, long version) {
    public enum Type { ORDER_BACKORDERED, ORDER_INVENTORY_ALLOCATED, ORDER_PENDING, ORDER_APPROVED, ORDER_DENIED, ORDER_COMPLETED }
    public enum DeliveryStatus { PENDING, DELIVERED }

    public static CustomerNotification forOrder(Order order, Type type, Instant occurredAt) {
        String shortId = order.id().substring(0, Math.min(8, order.id().length()));
        String title;
        String message;
        switch (type) {
            case ORDER_BACKORDERED -> {
                title = "Order backordered";
                message = "Order " + shortId + " is safely queued until the supplier replenishes inventory.";
            }
            case ORDER_INVENTORY_ALLOCATED -> {
                title = "Inventory allocated";
                message = "Inventory is now reserved for order " + shortId + ".";
            }
            case ORDER_PENDING -> {
                title = "Order awaiting review";
                message = "Order " + shortId + " is waiting for administrator approval.";
            }
            case ORDER_APPROVED -> {
                title = order.reviewedAt() == null ? "Order confirmed" : "Order approved";
                message = order.reviewedAt() == null
                        ? "Order " + shortId + " was confirmed and sent to the supplier."
                        : "Order " + shortId + " was approved and released to the supplier.";
            }
            case ORDER_DENIED -> {
                title = "Order denied";
                message = "Order " + shortId + " was denied. Its reserved inventory was released.";
            }
            case ORDER_COMPLETED -> {
                title = "Order completed";
                message = "Order " + shortId + " was fulfilled by the supplier.";
            }
            default -> throw new IllegalArgumentException("Unsupported notification type " + type);
        }
        return new CustomerNotification(order.id() + ":" + type.name(), order.customerId(), order.id(), type,
                title, message, occurredAt, DeliveryStatus.PENDING, 0, occurredAt,
                null, null, null, 0);
    }
}
