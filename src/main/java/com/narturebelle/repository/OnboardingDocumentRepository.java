package com.narturebelle.repository;

import com.narturebelle.model.OnboardingDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnboardingDocumentRepository extends JpaRepository<OnboardingDocument, Long> {
    List<OnboardingDocument> findByActiveTrue();
}
