package com.narturebelle.exception;

/** Maps to 404. */
public class ProviderNotFoundException extends RuntimeException {
    public ProviderNotFoundException(String message) {
        super(message);
    }
}
