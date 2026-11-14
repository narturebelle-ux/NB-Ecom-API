package com.narturebelle.providers.service;

import com.narturebelle.mail.MailQueue;
import org.springframework.stereotype.Component;

/** Status emails for care professionals and the mothers their changes affect. Sent by NB-Ecom-Notifications-API, after commit. */
@Component
public class ApplicationNotifier {

    private static final String SIGN_OFF = "\n\n-- The NartureBelle team";

    private final MailQueue mailQueue;

    public ApplicationNotifier(MailQueue mailQueue) {
        this.mailQueue = mailQueue;
    }

    public void notifyUser(Long userId, String subject, String body) {
        notifyUser("PROVIDER_APPLICATION_STATUS", userId, subject, body);
    }

    /** With its own event type, so NB-Ecom-Notifications-API can send its designed version (e.g. PROVIDER_LIVE). */
    public void notifyUser(String eventType, Long userId, String subject, String body) {
        mailQueue.sendToUser(eventType, userId, subject, body + SIGN_OFF);
    }
}
