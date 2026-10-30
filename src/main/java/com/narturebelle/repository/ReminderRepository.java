package com.narturebelle.repository;

import com.narturebelle.model.Reminder;
import com.narturebelle.model.ReminderSourceType;
import com.narturebelle.model.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ReminderStatus status);

    List<Reminder> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Reminder> findByUserIdAndSourceTypeAndSourceIdAndStatus(
            Long userId, ReminderSourceType sourceType, Long sourceId, ReminderStatus status);
}
