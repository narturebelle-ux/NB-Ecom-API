package com.narturebelle.exception;

/** Unknown referral id (admin confirm). Maps to 404. */
public class HospitalReferralNotFoundException extends RuntimeException {
    public HospitalReferralNotFoundException(String message) {
        super(message);
    }
}
