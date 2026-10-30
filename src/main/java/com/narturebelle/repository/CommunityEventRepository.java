package com.narturebelle.repository;

import com.narturebelle.model.CommunityEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CommunityEventRepository extends JpaRepository<CommunityEvent, Long> {

    List<CommunityEvent> findByEndsAtAfterOrderByStartsAtAsc(LocalDateTime now);

    List<CommunityEvent> findAllByOrderByStartsAtAsc();

    /** Serialises concurrent RSVPs to one event so the capacity check-then-insert can't oversell the last spot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CommunityEvent e where e.id = :id")
    Optional<CommunityEvent> findByIdForUpdate(@Param("id") Long id);
}
