package com.narturebelle.repository;

import com.narturebelle.model.UploadIntent;
import com.narturebelle.model.UploadPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UploadIntentRepository extends JpaRepository<UploadIntent, Long> {

    Optional<UploadIntent> findByObjectPathAndPurpose(String objectPath, UploadPurpose purpose);

    List<UploadIntent> findTop200ByExpiresAtBefore(LocalDateTime cutoff);
}
