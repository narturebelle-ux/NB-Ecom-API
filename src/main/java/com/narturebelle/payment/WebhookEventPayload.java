package com.narturebelle.payment;

import com.narturebelle.model.WebhookEventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Internal only — never serialized directly to a client. A parsed, provider-agnostic webhook event. */
public record WebhookEventPayload(
        String eventId,
        WebhookEventType type,
        Long paymentId,
        String providerReference,
        BigDecimal amount,
        LocalDateTime occurredAt
) {
}
