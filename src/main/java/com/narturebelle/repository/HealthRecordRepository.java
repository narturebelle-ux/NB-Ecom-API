package com.narturebelle.repository;

import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HealthRecordRepository extends JpaRepository<HealthRecord, Long> {
    List<HealthRecord> findByUserIdAndStatusNotOrderByCreatedAtDesc(Long userId, RecordStatus excludedStatus);

    /** Bytes counting towards the per-mother quota: not deleted, and not a video already past its removal date. */
    @Query("select coalesce(sum(r.sizeBytes), 0) from HealthRecord r where r.userId = :userId"
            + " and r.status <> com.narturebelle.model.RecordStatus.DELETED"
            + " and (r.removalAt is null or r.removalAt > :now)")
    long sumActiveBytes(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Videos the daily job may need to act on: not deleted, with a removal date in the window. */
    @Query("select r from HealthRecord r where r.kind = com.narturebelle.model.FileKind.VIDEO"
            + " and r.status <> com.narturebelle.model.RecordStatus.DELETED"
            + " and r.removalAt > :from and r.removalAt <= :to order by r.id")
    List<HealthRecord> findVideosWithRemovalBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
