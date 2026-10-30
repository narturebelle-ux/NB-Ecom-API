package com.narturebelle.exception;

/** Maps to 404. */
public class ServiceOfferingNotFoundException extends RuntimeException {
    public ServiceOfferingNotFoundException(String message) {
        super(message);
    }
}
