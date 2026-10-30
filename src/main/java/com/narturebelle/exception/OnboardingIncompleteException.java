package com.narturebelle.exception;

import java.util.List;

/** Thrown by POST /me/onboarding/complete when a required step is still missing. Maps to 409. */
public class OnboardingIncompleteException extends RuntimeException {

    private final List<String> missingSteps;

    public OnboardingIncompleteException(List<String> missingSteps) {
        super("Onboarding is not yet complete: " + String.join(", ", missingSteps));
        this.missingSteps = missingSteps;
    }

    public List<String> getMissingSteps() { return missingSteps; }
}
