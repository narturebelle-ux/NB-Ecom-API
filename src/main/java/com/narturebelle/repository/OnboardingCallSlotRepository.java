package com.narturebelle.repository;

import com.narturebelle.model.OnboardingCallSlot;
import com.narturebelle.model.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OnboardingCallSlotRepository extends JpaRepository<OnboardingCallSlot, Long> {

    boolean existsByCoordinatorIdAndStartAtBeforeAndEndAtAfter(Long coordinatorId, LocalDateTime end, LocalDateTime start);

    List<OnboardingCallSlot> findByStatusAndStartAtAfterOrderByStartAtAsc(SlotStatus status, LocalDateTime after);

    /** Conditional claim, same pattern as ProviderSlotRepository.claimSlot: 0 rows means lost the race. */
    @Modifying
    @Query("update OnboardingCallSlot s set s.status = com.narturebelle.model.SlotStatus.BOOKED "
            + "where s.id = :id and s.status = com.narturebelle.model.SlotStatus.OPEN")
    int claimSlot(@Param("id") Long id);
}
