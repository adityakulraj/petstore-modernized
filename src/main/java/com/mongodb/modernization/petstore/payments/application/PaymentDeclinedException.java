package com.mongodb.modernization.petstore.payments.application;

public class PaymentDeclinedException extends RuntimeException {
    /** Creates a payment declined exception and wires its required collaborators. */
    public PaymentDeclinedException(String message) { super(message); }
}
