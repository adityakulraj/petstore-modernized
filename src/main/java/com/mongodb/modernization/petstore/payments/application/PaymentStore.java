package com.mongodb.modernization.petstore.payments.application;

import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.payments.domain.Payment;

import java.time.Instant;
import java.util.List;

/** Operations join the caller's order transaction; adapters must use optimistic or conditional updates. */
public interface PaymentStore {
    Payment authorize(Order order, String paymentToken, Instant when);
    Payment capture(Order order, Instant when);
    Payment voidAuthorization(Order order, Instant when);
    Payment refund(Order order, Instant when);
    List<Payment> payments(String customerId);
}
