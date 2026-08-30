package com.mongodb.modernization.petstore.orders.application;

import com.mongodb.modernization.petstore.orders.domain.Order;

public interface CustomerOrderActionStore {
    Order cancel(String customerId, String orderId, long expectedVersion, String idempotencyKey, String reason);
    Order refund(String customerId, String orderId, long expectedVersion, String idempotencyKey, String reason);
}
