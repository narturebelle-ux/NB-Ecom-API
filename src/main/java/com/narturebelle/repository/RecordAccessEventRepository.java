package com.narturebelle.repository;

import com.narturebelle.model.RecordAccessEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecordAccessEventRepository extends JpaRepository<RecordAccessEvent, Long> {
    List<RecordAccessEvent> findByRecordIdOrderByOccurredAtDesc(Long recordId);
}
