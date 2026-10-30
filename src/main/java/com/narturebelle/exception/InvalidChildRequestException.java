package com.narturebelle.exception;

/** Cross-field checks Bean Validation can't express (e.g. a dose given before the child's birth). Maps to 400 VALIDATION_FAILED. */
public class InvalidChildRequestException extends RuntimeException {

    private final String field;

    public InvalidChildRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
