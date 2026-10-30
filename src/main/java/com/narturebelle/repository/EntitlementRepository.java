package com.narturebelle.repository;

import com.narturebelle.model.Entitlement;
import com.narturebelle.model.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EntitlementRepository extends JpaRepository<Entitlement, Long> {
    List<Entitlement> findByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByOrderIdAndExpiresAtAfter(Long orderId, java.time.LocalDateTime time);
    boolean existsByOrderId(Long orderId);

    @Query("select case when count(e) > 0 then true else false end from Entitlement e "
            + "where e.userId = :userId and e.professionalType = :professionalType "
            + "and e.usedSessions < e.totalSessions and e.expiresAt > :now")
    boolean existsConsumable(@Param("userId") Long userId,
                              @Param("professionalType") Role professionalType,
                              @Param("now") LocalDateTime now);

    /**
     * FIFO candidate for consumption: the non-expired, non-exhausted entitlement for this user+type
     * with the earliest expiresAt. Locked for update — this is a "find-then-increment" pattern, not
     * a "client has it open and submits an edit" pattern, so a pessimistic lock is the right tool
     * here rather than optimistic (@Version) retry logic.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Entitlement e where e.userId = :userId and e.professionalType = :professionalType "
            + "and e.usedSessions < e.totalSessions and e.expiresAt > :now order by e.expiresAt asc")
    List<Entitlement> findConsumableForUpdate(@Param("userId") Long userId,
                                               @Param("professionalType") Role professionalType,
                                               @Param("now") LocalDateTime now);
}
