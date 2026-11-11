package com.narturebelle.repository;

import com.narturebelle.model.VideoRetentionNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface VideoRetentionNoticeRepository extends JpaRepository<VideoRetentionNotice, Long> {
    boolean existsByRecordIdAndKindAndCycleRemovalAt(Long recordId, String kind, LocalDateTime cycleRemovalAt);

    List<VideoRetentionNotice> findByRecordIdOrderByIdAsc(Long recordId);
}
