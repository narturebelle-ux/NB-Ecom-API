package com.narturebelle.exception;

/** Thrown when booking against a provider that isn't VERIFIED/ACTIVE. Maps to 409. */
public class ProviderNotBookableException extends RuntimeException {
    public ProviderNotBookableException(String message) {
        super(message);
    }
}
