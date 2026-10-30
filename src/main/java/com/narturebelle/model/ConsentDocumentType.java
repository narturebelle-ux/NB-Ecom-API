package com.narturebelle.model;

/**
 * required = must be granted before anything personalised (@RequireConsent, pendingConsentTypes).
 * Non-required types are asked for only at the feature that needs them -- RECORDS_STORAGE before
 * the first health-record upload -- so adding one never locks existing users out of the app.
 */
public enum ConsentDocumentType {
    TERMS(true),
    PRIVACY(true),
    RECORDS_STORAGE(false);

    private final boolean required;

    ConsentDocumentType(boolean required) {
        this.required = required;
    }

    public boolean isRequired() { return required; }
}
