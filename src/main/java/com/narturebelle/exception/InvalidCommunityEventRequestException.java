package com.narturebelle.exception;

/** Cross-field event checks (endsAt after startsAt). Maps to 400 VALIDATION_FAILED. */
public class InvalidCommunityEventRequestException extends RuntimeException {

    private final String field;

    public InvalidCommunityEventRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
