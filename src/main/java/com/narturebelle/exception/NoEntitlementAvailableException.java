package com.narturebelle.exception;

/** Thrown when the user has no remaining session of the required professional type. Maps to 409. */
public class NoEntitlementAvailableException extends RuntimeException {
    public NoEntitlementAvailableException(String message) {
        super(message);
    }
}
