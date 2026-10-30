package com.narturebelle.repository;

import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentRepository extends JpaRepository<Consent, Long> {
    List<Consent> findByUserIdOrderByGrantedAtDesc(Long userId);

    boolean existsByUserIdAndDocumentTypeAndDocumentVersionAndRevokedAtIsNull(
            Long userId, ConsentDocumentType documentType, Integer documentVersion);

    /** Most recent non-revoked grant for a type, regardless of version — what "revoke" acts on. */
    Optional<Consent> findFirstByUserIdAndDocumentTypeAndRevokedAtIsNullOrderByGrantedAtDesc(
            Long userId, ConsentDocumentType documentType);

    /** Every non-revoked grant of a type, any version. */
    List<Consent> findByUserIdAndDocumentTypeAndRevokedAtIsNull(Long userId, ConsentDocumentType documentType);

    boolean existsByUserIdAndDocumentTypeAndRevokedAtIsNull(Long userId, ConsentDocumentType documentType);
}
