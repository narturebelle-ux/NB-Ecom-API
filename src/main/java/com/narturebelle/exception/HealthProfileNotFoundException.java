package com.narturebelle.exception;

/** No health profile saved yet. Maps to 404 HEALTH_PROFILE_NOT_FOUND. */
public class HealthProfileNotFoundException extends RuntimeException {
    public HealthProfileNotFoundException(String message) {
        super(message);
    }
}
