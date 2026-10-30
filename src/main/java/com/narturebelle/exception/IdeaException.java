package com.narturebelle.exception;

/** Idea-pool errors, mapped by status + code: 404 IDEA_NOT_FOUND, 409 IDEA_POOL_CLOSED / IDEA_NOT_OPEN /
 * IDEA_SELF_VOTE, 400 IMPACT_NOTE_REQUIRED, 429 TOO_MANY_REQUESTS. */
public class IdeaException extends RuntimeException {

    private final int status;
    private final String code;

    public IdeaException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
}
