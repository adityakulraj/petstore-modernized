package com.mongodb.modernization.petstore.payments.application;

import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.payments.domain.Payment;

import java.time.Instant;
import java.util.List;

/** Operations join the caller's order transaction; adapters must use optimistic or conditional updates. */
public interface PaymentStore {
    /** Executes the authorize persistence operation against the selected database. */
    Payment authorize(Order order, String paymentToken, Instant when);
    /** Executes the capture persistence operation against the selected database. */
    Payment capture(Order order, Instant when);
    /** Executes the void authorization persistence operation against the selected database. */
    Payment voidAuthorization(Order order, Instant when);
    /** Executes the refund persistence operation against the selected database. */
    Payment refund(Order order, Instant when);
    /** Executes the payments persistence operation against the selected database. */
    List<Payment> payments(String customerId);
}
