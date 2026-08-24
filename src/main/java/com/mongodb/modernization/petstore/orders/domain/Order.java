package com.mongodb.modernization.petstore.orders.domain;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.shared.domain.Address;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Order(String id, String customerId, String idempotencyKey, Instant createdAt,
                    String status, Address shippingAddress, List<OrderLine> lines, BigDecimal total) {
    public Order {
        lines = List.copyOf(lines);
    }

    public static Order placed(String id, String customerId, String idempotencyKey, Instant createdAt,
                               Address address, Cart cart) {
        return new Order(id, customerId, idempotencyKey, createdAt, "PLACED", address,
                cart.lines().stream().map(OrderLine::from).toList(), cart.total());
    }
}
