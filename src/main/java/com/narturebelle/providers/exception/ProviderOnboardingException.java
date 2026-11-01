package com.narturebelle.providers.exception;

import java.util.Map;

/** An expected failure with an API error code, in the same {code, message} shape as NB-Ecom-API. */
public class ProviderOnboardingException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, Object> details;

    public ProviderOnboardingException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ProviderOnboardingException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, Object> getDetails() { return details; }
}
