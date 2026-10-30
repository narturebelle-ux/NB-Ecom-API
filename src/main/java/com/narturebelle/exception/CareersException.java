package com.narturebelle.exception;

/** Careers/feedback errors, mapped by status + code: 404 JOB_NOT_FOUND, 409 JOB_CLOSED, 413 RESUME_TOO_LARGE,
 * 400 RESUME_TYPE_NOT_ALLOWED / VALIDATION_FAILED (with a field), 429 TOO_MANY_REQUESTS. */
public class CareersException extends RuntimeException {

    private final int status;
    private final String code;
    private final String field;

    public CareersException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public CareersException(int status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
    public String getField() { return field; }
}
