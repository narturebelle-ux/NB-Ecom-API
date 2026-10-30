package com.narturebelle.exception;

/** Thrown when a profile update's new phone number is already taken by another account. Maps to 400. */
public class PhoneInUseException extends RuntimeException {
    public PhoneInUseException(String message) {
        super(message);
    }
}
