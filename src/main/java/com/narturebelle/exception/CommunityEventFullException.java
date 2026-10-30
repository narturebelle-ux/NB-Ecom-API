package com.narturebelle.exception;

/** RSVP to an event with no spots left. Maps to 409 EVENT_FULL. */
public class CommunityEventFullException extends RuntimeException {
    public CommunityEventFullException(String message) {
        super(message);
    }
}
