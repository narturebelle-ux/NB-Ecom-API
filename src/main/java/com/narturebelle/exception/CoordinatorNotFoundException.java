package com.narturebelle.exception;

/** Unknown (or, when creating slots, inactive) coordinator id. Maps to 404 COORDINATOR_NOT_FOUND. */
public class CoordinatorNotFoundException extends RuntimeException {
    public CoordinatorNotFoundException(String message) {
        super(message);
    }
}
