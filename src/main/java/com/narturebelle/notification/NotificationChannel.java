package com.narturebelle.notification;

import com.narturebelle.model.NotificationChannelType;

/** One implementation per NotificationChannelType. IN_APP doesn't need one -- the Reminder row
 * itself IS the in-app notification; NotificationService creates it directly. */
public interface NotificationChannel {
    NotificationChannelType type();
    void send(Long userId, String title, String body);
}
