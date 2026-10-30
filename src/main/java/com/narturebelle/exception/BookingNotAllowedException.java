package com.narturebelle.exception;

/** Generic wrong-state-for-action (e.g. completing a booking that's already COMPLETED). Maps to 409. */
public class BookingNotAllowedException extends RuntimeException {
    public BookingNotAllowedException(String message) {
        super(message);
    }
}
