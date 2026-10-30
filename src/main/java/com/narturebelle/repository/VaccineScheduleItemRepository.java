package com.narturebelle.repository;

import com.narturebelle.model.VaccineScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VaccineScheduleItemRepository extends JpaRepository<VaccineScheduleItem, Long> {
    List<VaccineScheduleItem> findByScheduleVersionIdOrderBySortOrderAsc(Long scheduleVersionId);
}
