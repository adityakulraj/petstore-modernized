package com.mongodb.modernization.petstore.shared.application;

public class NotFoundException extends RuntimeException {
    /** Creates a not found exception and wires its required collaborators. */
    public NotFoundException(String message) { super(message); }
}
