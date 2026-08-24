package com.mongodb.modernization.petstore.orders.domain;

import com.mongodb.modernization.petstore.cart.domain.CartLine;

import java.math.BigDecimal;

public record OrderLine(String productId, String productName, BigDecimal unitPrice, int quantity,
                        BigDecimal subtotal) {
    public static OrderLine from(CartLine line) {
        return new OrderLine(line.productId(), line.productName(), line.unitPrice(), line.quantity(), line.subtotal());
    }
}
