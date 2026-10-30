package com.narturebelle.exception;

/** A deletion-request state conflict. Maps to 409 with the given code: DELETION_ALREADY_REQUESTED,
 * DELETION_NOT_CANCELLABLE or INVALID_DELETION_STATUS_TRANSITION. */
public class DeletionRequestConflictException extends RuntimeException {

    private final String code;

    public DeletionRequestConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
