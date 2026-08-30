package com.mongodb.modernization.petstore.notifications.domain;

import com.mongodb.modernization.petstore.orders.domain.Order;

import java.time.Instant;

public record CustomerNotification(String id, String customerId, String orderId, Type type,
                                   String title, String message, Instant createdAt,
                                   DeliveryStatus deliveryStatus, int deliveryAttempts,
                                   Instant nextAttemptAt, Instant deliveredAt, String lastError,
                                   Instant readAt, long version) {
    public enum Type { ORDER_BACKORDERED, ORDER_INVENTORY_ALLOCATED, ORDER_PENDING, ORDER_APPROVED, ORDER_DENIED,
        ORDER_COMPLETED, ORDER_CANCELLED, ORDER_REFUNDED, PAYMENT_AUTHORIZED, PAYMENT_CAPTURED, PAYMENT_VOIDED,
        PAYMENT_REFUNDED }
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
            case ORDER_CANCELLED -> {
                title = "Order cancelled";
                message = "Order " + shortId + " was cancelled. Any reserved inventory was released.";
            }
            case ORDER_REFUNDED -> {
                title = "Order refunded";
                message = "The refund for completed order " + shortId + " has been recorded.";
            }
            case PAYMENT_AUTHORIZED -> {
                title = "Payment authorized";
                message = "Payment for order " + shortId + " was authorized; no card data was stored.";
            }
            case PAYMENT_CAPTURED -> {
                title = "Payment captured";
                message = "Payment for order " + shortId + " was captured after supplier fulfilment.";
            }
            case PAYMENT_VOIDED -> {
                title = "Payment authorization voided";
                message = "The uncaptured payment authorization for order " + shortId + " was voided.";
            }
            case PAYMENT_REFUNDED -> {
                title = "Payment refunded";
                message = "The captured payment for order " + shortId + " was refunded.";
            }
            default -> throw new IllegalArgumentException("Unsupported notification type " + type);
        }
        return new CustomerNotification(order.id() + ":" + type.name(), order.customerId(), order.id(), type,
                title, message, occurredAt, DeliveryStatus.PENDING, 0, occurredAt,
                null, null, null, 0);
    }
}
