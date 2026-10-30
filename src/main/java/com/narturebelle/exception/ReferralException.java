package com.narturebelle.exception;

/** Referral errors, mapped by status + code: 404 REFERRAL_CODE_NOT_FOUND, 409 REFERRAL_ALREADY_CLAIMED /
 * REFERRAL_SELF / REFERRAL_NOT_NEW_CUSTOMER. */
public class ReferralException extends RuntimeException {

    private final int status;
    private final String code;

    public ReferralException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
}
