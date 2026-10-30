package com.narturebelle.exception;

/** No (non-cancelled) deletion request for this user, or an unknown id on the admin side. Maps to 404. */
public class DeletionRequestNotFoundException extends RuntimeException {
    public DeletionRequestNotFoundException(String message) {
        super(message);
    }
}
