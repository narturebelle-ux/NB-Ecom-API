package com.narturebelle.exception;

/** Generic wrong-state-for-action, mirrors BookingNotAllowedException. Maps to 409. */
public class RecordNotAllowedException extends RuntimeException {
    public RecordNotAllowedException(String message) {
        super(message);
    }
}
