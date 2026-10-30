package com.narturebelle.exception;

/** Maps to 409. */
public class FeedbackNotAllowedException extends RuntimeException {
    public FeedbackNotAllowedException(String message) {
        super(message);
    }
}
