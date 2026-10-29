package com.narturebelle.exception;

/** Thrown when a webhook/mock event references an unknown payment. Maps to 404. */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }
}
