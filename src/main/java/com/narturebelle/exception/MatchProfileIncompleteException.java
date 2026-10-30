package com.narturebelle.exception;

import java.util.List;

/** Matching needs the intake's languages. Maps to 409 MATCH_PROFILE_INCOMPLETE with the missing fields. */
public class MatchProfileIncompleteException extends RuntimeException {

    private final List<String> missing;

    public MatchProfileIncompleteException(List<String> missing) {
        super("Add your languages in onboarding to see matched providers: " + String.join(", ", missing));
        this.missing = missing;
    }

    public List<String> getMissing() { return missing; }
}
