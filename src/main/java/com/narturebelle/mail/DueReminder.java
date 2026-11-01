package com.narturebelle.mail;

import java.time.LocalDateTime;

/** One reminder the Care API says is due. NB-Ecom-Mails-API owns the rows and the wording. dedupeKey is null when
 * the source ID alone identifies the reminder, and opaque otherwise, so no health detail leaves this API. */
public record DueReminder(Long userId, String type, String sourceType, Long sourceId, LocalDateTime dueAt, String dedupeKey) {
}
