package com.mongodb.modernization.petstore.orders.application;

import com.mongodb.modernization.petstore.orders.domain.Order;

import java.util.List;

public interface AdminOrderStore {
    /** Executes the orders persistence operation against the selected database. */
    List<Order> orders();
    /** Executes the review persistence operation against the selected database. */
    Order review(String orderId, long expectedVersion, Decision decision, String reviewer);

    enum Decision { APPROVED, DENIED }
}
