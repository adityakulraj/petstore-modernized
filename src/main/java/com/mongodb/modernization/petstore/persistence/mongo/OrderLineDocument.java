package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.orders.domain.OrderLine;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

class OrderLineDocument {
    String productId; String productName;
    @Field(targetType = FieldType.DECIMAL128) BigDecimal unitPrice;
    int quantity;
    @Field(targetType = FieldType.DECIMAL128) BigDecimal subtotal;

    OrderLineDocument() {}
    OrderLineDocument(OrderLine line) {
        productId = line.productId(); productName = line.productName(); unitPrice = line.unitPrice();
        quantity = line.quantity(); subtotal = line.subtotal();
    }
    OrderLine toDomain() { return new OrderLine(productId, productName, unitPrice, quantity, subtotal); }
}
