package com.narturebelle.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutgoingEmailRepository extends JpaRepository<OutgoingEmail, Long> {

    /** Least recently tried first, so rows that keep failing can't hold up the rest. SKIP LOCKED so two scheduler runs
     * never pick the same row at once. Mails' event-ID check is the real guard. */
    @Query(value = "select id from outgoing_emails where status = 'PENDING' and created_at < :cutoff "
            + "and (last_attempt_at is null or last_attempt_at < :cutoff) order by last_attempt_at nulls first, id limit :limit for update skip locked",
            nativeQuery = true)
    List<Long> lockDue(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Modifying
    @Query("update OutgoingEmail e set e.lastAttemptAt = :now where e.id in :ids")
    void stampAttempt(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);
}
