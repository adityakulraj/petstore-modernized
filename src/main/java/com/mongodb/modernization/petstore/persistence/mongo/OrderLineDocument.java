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

    /** Creates an empty MongoDB order-line representation for object mapping. */
    OrderLineDocument() {}
    /** Creates a MongoDB order-line representation from a domain snapshot. */
    OrderLineDocument(OrderLine line) {
        productId = line.productId(); productName = line.productName(); unitPrice = line.unitPrice();
        quantity = line.quantity(); subtotal = line.subtotal();
    }
    /** Maps this persistence representation to the corresponding domain model. */
    OrderLine toDomain() { return new OrderLine(productId, productName, unitPrice, quantity, subtotal); }
}
