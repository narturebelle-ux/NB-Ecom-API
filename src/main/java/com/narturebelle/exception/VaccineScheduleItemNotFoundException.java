package com.narturebelle.exception;

/** Schedule item id that isn't part of the currently active schedule version. Maps to 404. */
public class VaccineScheduleItemNotFoundException extends RuntimeException {
    public VaccineScheduleItemNotFoundException(String message) {
        super(message);
    }
}
