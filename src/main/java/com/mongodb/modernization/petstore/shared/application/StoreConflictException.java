package com.mongodb.modernization.petstore.shared.application;

public class StoreConflictException extends RuntimeException {
    /** Creates a store conflict exception and wires its required collaborators. */
    public StoreConflictException(String message) { super(message); }
    /** Creates a store conflict exception and wires its required collaborators. */
    public StoreConflictException(String message, Throwable cause) { super(message, cause); }
}
