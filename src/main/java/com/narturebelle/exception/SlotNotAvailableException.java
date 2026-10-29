package com.narturebelle.exception;

/** Thrown when a slot is no longer OPEN (already booked, or lost the claim race). Maps to 409. */
public class SlotNotAvailableException extends RuntimeException {
    public SlotNotAvailableException(String message) {
        super(message);
    }
}
