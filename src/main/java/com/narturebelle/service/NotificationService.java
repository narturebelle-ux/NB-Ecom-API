package com.narturebelle.service;

import com.narturebelle.model.NotificationChannelType;
import com.narturebelle.model.Reminder;
import com.narturebelle.model.ReminderSourceType;
import com.narturebelle.model.ReminderStatus;
import com.narturebelle.model.ReminderType;
import com.narturebelle.notification.NotificationChannel;
import com.narturebelle.repository.ReminderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The in-app channel is always the Reminder row itself -- no separate dispatch step. Other channels
 * (email/SMS/WhatsApp) are only invoked if the user's NotificationPreference enables them, and today
 * every one of those implementations is a NoOp that logs intent (see com.narturebelle.notification).
 */
@Service
public class NotificationService {

    private final ReminderRepository reminderRepository;
    private final NotificationPreferenceService notificationPreferenceService;
    private final Map<NotificationChannelType, NotificationChannel> channels;

    public NotificationService(ReminderRepository reminderRepository,
                                NotificationPreferenceService notificationPreferenceService,
                                List<NotificationChannel> channelBeans) {
        this.reminderRepository = reminderRepository;
        this.notificationPreferenceService = notificationPreferenceService;
        this.channels = new EnumMap<>(NotificationChannelType.class);
        for (NotificationChannel channel : channelBeans) {
            this.channels.put(channel.type(), channel);
        }
    }

    /** Idempotent: a repeated call for the same (userId, sourceType, sourceId) while the previous
     * reminder is still ACTIVE is a no-op, so a scheduled job can call this on every run without
     * creating duplicates. */
    public void notify(Long userId, ReminderType type, String title, String body,
                        ReminderSourceType sourceType, Long sourceId, LocalDateTime dueAt) {
        boolean alreadyActive = reminderRepository
                .findByUserIdAndSourceTypeAndSourceIdAndStatus(userId, sourceType, sourceId, ReminderStatus.ACTIVE)
                .isPresent();
        if (alreadyActive) {
            return;
        }

        Reminder reminder = new Reminder();
        reminder.setUserId(userId);
        reminder.setType(type);
        reminder.setTitle(title);
        reminder.setBody(body);
        reminder.setDueAt(dueAt);
        reminder.setSourceType(sourceType);
        reminder.setSourceId(sourceId);
        reminder.setStatus(ReminderStatus.ACTIVE);
        reminderRepository.save(reminder);

        for (NotificationChannelType channelType : List.of(
                NotificationChannelType.EMAIL, NotificationChannelType.SMS, NotificationChannelType.WHATSAPP)) {
            if (notificationPreferenceService.isEnabled(userId, channelType)) {
                NotificationChannel channel = channels.get(channelType);
                if (channel != null) {
                    channel.send(userId, title, body);
                }
            }
        }
    }

    /** Transactional emails (e.g. "here's your signed document") are not reminders -- they don't
     * create a dismissible Reminder row, and they bypass the EMAIL channel preference, since the
     * user's own action (signing) is the real consent here, not a general notification opt-in.
     * Still a NoOp today, same as every other EMAIL send until a real provider exists. */
    public void sendTransactionalEmail(Long userId, String title, String body) {
        NotificationChannel email = channels.get(NotificationChannelType.EMAIL);
        if (email != null) {
            email.send(userId, title, body);
        }
    }
}
