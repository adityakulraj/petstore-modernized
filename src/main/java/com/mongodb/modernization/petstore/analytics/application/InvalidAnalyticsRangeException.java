package com.mongodb.modernization.petstore.analytics.application;

public class InvalidAnalyticsRangeException extends RuntimeException {
    public InvalidAnalyticsRangeException(String message) { super(message); }
}
