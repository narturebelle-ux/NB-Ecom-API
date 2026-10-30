package com.narturebelle.exception;

/** Thrown by POST /api/v1/me/journeys when the user already has a non-CLOSED journey. Maps to 409. */
public class JourneyAlreadyActiveException extends RuntimeException {
    public JourneyAlreadyActiveException(String message) {
        super(message);
    }
}
