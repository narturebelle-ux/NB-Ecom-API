package com.narturebelle.repository;

import com.narturebelle.model.CommunityEventRsvp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityEventRsvpRepository extends JpaRepository<CommunityEventRsvp, Long> {
    long countByEventId(Long eventId);
    Optional<CommunityEventRsvp> findByEventIdAndUserId(Long eventId, Long userId);
    List<CommunityEventRsvp> findByUserId(Long userId);
}
