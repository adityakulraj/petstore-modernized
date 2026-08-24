package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.cart.domain.CartLine;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
class CartLineJpa {
    @Column(name = "PRODUCT_ID", nullable = false, length = 40) String productId;
    @Column(name = "PRODUCT_NAME", nullable = false, length = 120) String productName;
    @Column(name = "UNIT_PRICE", nullable = false, precision = 12, scale = 2) BigDecimal unitPrice;
    @Column(nullable = false) int quantity;

    protected CartLineJpa() {}
    CartLineJpa(CartLine line) {
        productId = line.productId(); productName = line.productName(); unitPrice = line.unitPrice(); quantity = line.quantity();
    }
    CartLine toDomain() { return new CartLine(productId, productName, unitPrice, quantity); }
}
