package com.mongodb.modernization.petstore.orders.domain;

import com.mongodb.modernization.petstore.cart.domain.CartLine;

import java.math.BigDecimal;

/** Immutable order-line snapshot that preserves the checkout-time name and price. */
public record OrderLine(String productId, String productName, BigDecimal unitPrice, int quantity,
                        BigDecimal subtotal) {
    /** Copies a mutable-cart line into historical order-line data. */
    public static OrderLine from(CartLine line) {
        return new OrderLine(line.productId(), line.productName(), line.unitPrice(), line.quantity(), line.subtotal());
    }
}
