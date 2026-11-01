package com.narturebelle.mail;

/** The body of POST /internal/v1/emails on NB-Ecom-Mails-API. Exactly one of userId and to is set. */
public record OutgoingMail(String eventId, String eventType, Long userId, String to, String replyTo, String subject,
                           String text) {
}
