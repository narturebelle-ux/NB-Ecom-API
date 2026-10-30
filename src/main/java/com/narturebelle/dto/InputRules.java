package com.narturebelle.dto;

import com.narturebelle.exception.InputValidationException;

import java.time.LocalDate;

/** Formats shared by every request DTO, so a phone number or link means the same thing on every form. */
public final class InputRules {

    /** Indian mobile: optional +91, then 10 digits starting 6-9. Blank passes (use @NotBlank when required). */
    public static final String PHONE = "((\\+91[ -]?)?[6-9][0-9]{4}[ -]?[0-9]{5})?";
    public static final String PHONE_MESSAGE = "must be a valid Indian mobile number, e.g. +91 98450 12345";

    /** Six digits, not starting with 0. Blank passes. */
    public static final String PINCODE = "([1-9][0-9]{5})?";
    public static final String PINCODE_MESSAGE = "must be a 6-digit PIN code";

    /** Blank passes. */
    public static final String HTTPS_URL = "(https://\\S+)?";
    public static final String HTTPS_URL_MESSAGE = "must be a link starting with https://";

    /** How far ahead a due date can be: a full-term pregnancy from today. */
    public static final int MAX_WEEKS_TO_DUE_DATE = 42;

    private InputRules() {}

    /** Stored form: +91 and the 10 digits, so the same number can't be saved two ways. Blank stays null. */
    public static String normalisePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        return "+91" + digits.substring(digits.length() - 10);
    }

    /** A due date while she's still pregnant: from today up to 42 weeks ahead. */
    public static void requireDueDate(String field, LocalDate dueDate) {
        LocalDate today = LocalDate.now();
        if (dueDate.isBefore(today)) {
            throw new InputValidationException(field, "Due date can't be in the past.");
        }
        if (dueDate.isAfter(today.plusWeeks(MAX_WEEKS_TO_DUE_DATE))) {
            throw new InputValidationException(field, "Due date can't be more than " + MAX_WEEKS_TO_DUE_DATE + " weeks away.");
        }
    }
}
