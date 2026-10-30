package com.narturebelle.exception;

/** RSVP to a cancelled or already-started event, or cancelling an event twice. Maps to 409 EVENT_NOT_OPEN. */
public class CommunityEventNotOpenException extends RuntimeException {
    public CommunityEventNotOpenException(String message) {
        super(message);
    }
}
