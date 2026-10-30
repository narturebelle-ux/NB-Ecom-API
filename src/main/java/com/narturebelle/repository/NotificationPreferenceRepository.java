package com.narturebelle.repository;

import com.narturebelle.model.NotificationChannelType;
import com.narturebelle.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findByUserId(Long userId);

    Optional<NotificationPreference> findByUserIdAndChannel(Long userId, NotificationChannelType channel);
}
