package com.narturebelle.exception;

/** Professional-portal errors, mapped by status + code: 404 BOOKING_NOT_FOUND, 409 SESSION_RECOMMENDATIONS_CLOSED. */
public class SessionRecommendationException extends RuntimeException {

    private final int status;
    private final String code;

    public SessionRecommendationException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
}
