package com.narturebelle.repository;

import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HealthRecordRepository extends JpaRepository<HealthRecord, Long> {
    List<HealthRecord> findByUserIdAndStatusNotOrderByCreatedAtDesc(Long userId, RecordStatus excludedStatus);
}
