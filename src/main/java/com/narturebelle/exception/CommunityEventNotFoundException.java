package com.narturebelle.exception;

/** Unknown Joy Circle event id. Maps to 404 EVENT_NOT_FOUND. */
public class CommunityEventNotFoundException extends RuntimeException {
    public CommunityEventNotFoundException(String message) {
        super(message);
    }
}
