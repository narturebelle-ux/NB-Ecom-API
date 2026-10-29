package com.narturebelle.exception;

/** Thrown when a user who already has an active onboarding call tries to book another. Maps to 409. */
public class OnboardingCallAlreadyBookedException extends RuntimeException {
    public OnboardingCallAlreadyBookedException(String message) {
        super(message);
    }
}
