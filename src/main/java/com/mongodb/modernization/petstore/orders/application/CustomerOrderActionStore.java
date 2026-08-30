package com.mongodb.modernization.petstore.orders.application;

import com.mongodb.modernization.petstore.orders.domain.Order;

public interface CustomerOrderActionStore {
    /** Executes the cancel persistence operation against the selected database. */
    Order cancel(String customerId, String orderId, long expectedVersion, String idempotencyKey, String reason);
    /** Executes the refund persistence operation against the selected database. */
    Order refund(String customerId, String orderId, long expectedVersion, String idempotencyKey, String reason);
}
