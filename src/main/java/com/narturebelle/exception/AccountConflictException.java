package com.narturebelle.exception;

/** A sign-in's email or phone belongs to another account, and we can't show it's the same person. Maps to 409. */
public class AccountConflictException extends RuntimeException {

    private final String code;

    public AccountConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
