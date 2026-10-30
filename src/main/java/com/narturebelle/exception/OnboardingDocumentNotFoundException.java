package com.narturebelle.exception;

/** Thrown when signing an unknown onboarding document id. Maps to 404. */
public class OnboardingDocumentNotFoundException extends RuntimeException {
    public OnboardingDocumentNotFoundException(String message) {
        super(message);
    }
}
