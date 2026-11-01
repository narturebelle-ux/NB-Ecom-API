package com.narturebelle.providers.repository;

import com.narturebelle.providers.model.ApplicationDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

    List<ApplicationDocument> findByApplicationId(Long applicationId);

    Optional<ApplicationDocument> findByApplicationIdAndQuestionKey(Long applicationId, String questionKey);
}
