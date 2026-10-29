package com.narturebelle.exception;

/** Covers both upload-allowlist violations and share-request validation — same shape as InvalidJourneyRequestException. Maps to 400 VALIDATION_FAILED. */
public class InvalidRecordRequestException extends RuntimeException {

    private final String field;

    public InvalidRecordRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
