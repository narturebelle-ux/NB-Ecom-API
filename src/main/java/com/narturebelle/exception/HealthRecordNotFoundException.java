package com.narturebelle.exception;

/** Maps to 404. */
public class HealthRecordNotFoundException extends RuntimeException {
    public HealthRecordNotFoundException(String message) {
        super(message);
    }
}
