package com.narturebelle.service;

import com.narturebelle.exception.InputValidationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** What makes a bookable time slot sensible, for provider slots and onboarding call slots alike. */
final class SlotRules {

    private static final Duration MAX_LENGTH = Duration.ofHours(8);

    private SlotRules() {}

    /** seen collects the windows already checked for this person, so a batch can't overlap itself. */
    static void check(LocalDateTime start, LocalDateTime end, List<LocalDateTime[]> seen) {
        if (!end.isAfter(start)) {
            throw new InputValidationException("slots", "A slot must end after it starts.");
        }
        if (!start.isAfter(LocalDateTime.now())) {
            throw new InputValidationException("slots", "A slot can't start in the past.");
        }
        if (Duration.between(start, end).compareTo(MAX_LENGTH) > 0) {
            throw new InputValidationException("slots", "A slot can't be longer than 8 hours.");
        }
        for (LocalDateTime[] other : seen) {
            if (start.isBefore(other[1]) && end.isAfter(other[0])) {
                throw new InputValidationException("slots", "Two of these slots overlap.");
            }
        }
        seen.add(new LocalDateTime[]{start, end});
    }
}
