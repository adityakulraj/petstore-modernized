package com.mongodb.modernization.petstore.shared.application;

public class StoreConflictException extends RuntimeException {
    public StoreConflictException(String message) { super(message); }
    public StoreConflictException(String message, Throwable cause) { super(message, cause); }
}
