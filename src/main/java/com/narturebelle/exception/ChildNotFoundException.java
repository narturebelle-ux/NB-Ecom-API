package com.narturebelle.exception;

/** Unknown child id, or one owned by someone else (404, not 403, so ids don't leak). Maps to 404. */
public class ChildNotFoundException extends RuntimeException {
    public ChildNotFoundException(String message) {
        super(message);
    }
}
