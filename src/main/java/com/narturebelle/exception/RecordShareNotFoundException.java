package com.narturebelle.exception;

/** Maps to 404. */
public class RecordShareNotFoundException extends RuntimeException {
    public RecordShareNotFoundException(String message) {
        super(message);
    }
}
