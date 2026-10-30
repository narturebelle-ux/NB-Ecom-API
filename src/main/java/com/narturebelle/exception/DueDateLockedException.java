package com.narturebelle.exception;

/** Moving the EDD too far from the one a plan was priced on. Maps to 409 DUE_DATE_LOCKED. */
public class DueDateLockedException extends RuntimeException {
    public DueDateLockedException(String message) {
        super(message);
    }
}
