package com.mongodb.modernization.petstore.analytics.application;

public class InvalidAnalyticsRangeException extends RuntimeException {
    /** Creates a invalid analytics range exception and wires its required collaborators. */
    public InvalidAnalyticsRangeException(String message) { super(message); }
}
