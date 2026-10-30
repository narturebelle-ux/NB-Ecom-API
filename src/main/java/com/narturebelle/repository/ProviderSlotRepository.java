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

    boolean existsByProviderIdAndStartAtBeforeAndEndAtAfter(Long providerId, LocalDateTime end, LocalDateTime start);

    List<ProviderSlot> findByProviderIdAndStatusAndStartAtAfterOrderByStartAtAsc(
            Long providerId, SlotStatus status, LocalDateTime after);

    /** Conditional claim: 0 rows affected means someone else booked it first (or it was never OPEN). */
    @Modifying
    @Query("update ProviderSlot s set s.status = com.narturebelle.model.SlotStatus.BOOKED "
            + "where s.id = :id and s.status = com.narturebelle.model.SlotStatus.OPEN")
    int claimSlot(@Param("id") Long id);
}
