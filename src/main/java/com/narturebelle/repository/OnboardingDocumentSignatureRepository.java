package com.narturebelle.repository;

import com.narturebelle.model.OnboardingDocumentSignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OnboardingDocumentSignatureRepository extends JpaRepository<OnboardingDocumentSignature, Long> {
    List<OnboardingDocumentSignature> findByUserId(Long userId);
    Optional<OnboardingDocumentSignature> findByUserIdAndDocumentId(Long userId, Long documentId);
}
