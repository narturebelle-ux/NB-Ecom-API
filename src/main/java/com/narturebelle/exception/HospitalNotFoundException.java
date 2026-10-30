package com.narturebelle.exception;

/** Unknown HospitalPartner id. Maps to 404. */
public class HospitalNotFoundException extends RuntimeException {
    public HospitalNotFoundException(String message) {
        super(message);
    }
}
