package com.narturebelle.exception;

/** Thrown when granting a consent document version that isn't the currently active one. Maps to 409. */
public class ConsentVersionOutdatedException extends RuntimeException {
    public ConsentVersionOutdatedException(String message) {
        super(message);
    }
}
