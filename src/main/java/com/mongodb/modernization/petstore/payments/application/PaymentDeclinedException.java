package com.mongodb.modernization.petstore.payments.application;

public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) { super(message); }
}
