package com.narturebelle.exception;

/** Maps to 409. */
public class InvalidProviderStatusTransitionException extends RuntimeException {
    public InvalidProviderStatusTransitionException(String message) {
        super(message);
    }
}
