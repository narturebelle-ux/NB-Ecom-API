package com.narturebelle.exception;

/** Thrown when an onboarding call slot is no longer OPEN. Maps to 409. */
public class OnboardingCallSlotNotAvailableException extends RuntimeException {
    public OnboardingCallSlotNotAvailableException(String message) {
        super(message);
    }
}
