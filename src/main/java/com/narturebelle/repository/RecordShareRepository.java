package com.narturebelle.repository;

import com.narturebelle.model.RecordShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecordShareRepository extends JpaRepository<RecordShare, Long> {
    List<RecordShare> findByRecordIdOrderByCreatedAtDesc(Long recordId);
    List<RecordShare> findByRecordIdAndRevokedAtIsNull(Long recordId);
    List<RecordShare> findByTargetProviderIdAndRevokedAtIsNull(Long providerId);
    List<RecordShare> findByTargetBookingIdInAndRevokedAtIsNull(List<Long> bookingIds);
}
