package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.cart.domain.CartLine;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

class CartLineDocument {
    String productId;
    String productName;
    @Field(targetType = FieldType.DECIMAL128) BigDecimal unitPrice;
    int quantity;

    CartLineDocument() {}
    CartLineDocument(CartLine line) {
        productId = line.productId(); productName = line.productName(); unitPrice = line.unitPrice(); quantity = line.quantity();
    }
    CartLine toDomain() { return new CartLine(productId, productName, unitPrice, quantity); }
}
