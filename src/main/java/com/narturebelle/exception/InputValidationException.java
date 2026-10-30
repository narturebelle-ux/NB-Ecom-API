package com.narturebelle.exception;

/** A rule a request annotation can't express (cross-field, or needing stored data). Maps to 400 VALIDATION_FAILED with fields. */
public class InputValidationException extends RuntimeException {

    private final String field;

    public InputValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
