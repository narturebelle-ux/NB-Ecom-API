package com.narturebelle.repository;

import com.narturebelle.model.ProviderSlot;
import com.narturebelle.model.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProviderSlotRepository extends JpaRepository<ProviderSlot, Long> {
    /** Any slot that still holds this time: open, or booked and possibly under way. Removed slots don't count. */
    @org.springframework.data.jpa.repository.Query("select count(s) > 0 from ProviderSlot s where s.providerId = :providerId"
            + " and s.status <> com.narturebelle.model.SlotStatus.CANCELLED and s.startAt < :endAt and s.endAt > :startAt")
    boolean overlaps(@org.springframework.data.repository.query.Param("providerId") Long providerId,
                     @org.springframework.data.repository.query.Param("startAt") java.time.LocalDateTime startAt,
                     @org.springframework.data.repository.query.Param("endAt") java.time.LocalDateTime endAt);


    boolean existsByProviderIdAndStartAtBeforeAndEndAtAfter(Long providerId, LocalDateTime end, LocalDateTime start);

    List<ProviderSlot> findByProviderIdAndStatusAndStartAtAfterOrderByStartAtAsc(
            Long providerId, SlotStatus status, LocalDateTime after);

    /** Conditional claim: 0 rows affected means someone else booked it first (or it was never OPEN). */
    @Modifying
    @Query("update ProviderSlot s set s.status = com.narturebelle.model.SlotStatus.BOOKED "
            + "where s.id = :id and s.status = com.narturebelle.model.SlotStatus.OPEN")
    int claimSlot(@Param("id") Long id);
}
