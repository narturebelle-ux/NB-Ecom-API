package com.narturebelle.money;

/** NB-Ecom-Payments-API couldn't answer. Maps to 503, so a money outage never reads as "she hasn't paid". */
public class MoneyUnavailableException extends RuntimeException {

    public MoneyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
