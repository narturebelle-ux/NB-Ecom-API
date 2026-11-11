package com.narturebelle.repository;

import com.narturebelle.model.PendingUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingUploadRepository extends JpaRepository<PendingUpload, Long> {
    Optional<PendingUpload> findByObjectPathAndUserId(String objectPath, Long userId);
}
