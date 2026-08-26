package com.mongodb.modernization.petstore.orders.application;

import com.mongodb.modernization.petstore.orders.domain.Order;

import java.util.List;

public interface AdminOrderStore {
    List<Order> orders();
    Order review(String orderId, long expectedVersion, Decision decision, String reviewer);

    enum Decision { APPROVED, DENIED }
}
