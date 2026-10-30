package com.narturebelle.service;

import com.narturebelle.model.Reminder;
import com.narturebelle.model.ReminderStatus;
import com.narturebelle.repository.ReminderRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;

    public ReminderService(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    public List<Reminder> listActive(Long userId) {
        return reminderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, ReminderStatus.ACTIVE);
    }

    public Optional<Reminder> find(Long id) {
        return reminderRepository.findById(id);
    }

    public Reminder dismiss(Reminder reminder, Long callerUserId) {
        if (!reminder.getUserId().equals(callerUserId)) {
            throw new AccessDeniedException("You cannot dismiss another user's reminder.");
        }
        reminder.setStatus(ReminderStatus.DISMISSED);
        reminder.setDismissedAt(LocalDateTime.now());
        return reminderRepository.save(reminder);
    }
}
