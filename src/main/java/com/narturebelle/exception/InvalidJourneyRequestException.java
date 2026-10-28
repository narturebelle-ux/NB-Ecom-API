package com.narturebelle.exception;

/** Thrown for journey request fields that fail business-rule validation outside Bean Validation (e.g. EDD bounds). Maps to 400 VALIDATION_FAILED. */
public class InvalidJourneyRequestException extends RuntimeException {

    private final String field;

    public InvalidJourneyRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
