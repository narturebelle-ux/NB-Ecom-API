package com.narturebelle.mail;

/** NB-Ecom-Notifications-API couldn't answer a call the current request can't finish without. */
public class MailsUnavailableException extends RuntimeException {
    public MailsUnavailableException(Throwable cause) {
        super("The notifications service is unavailable.", cause);
    }
}
