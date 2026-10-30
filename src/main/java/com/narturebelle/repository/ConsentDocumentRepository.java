package com.narturebelle.repository;

import com.narturebelle.model.ConsentDocument;
import com.narturebelle.model.ConsentDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentDocumentRepository extends JpaRepository<ConsentDocument, Long> {
    List<ConsentDocument> findByActiveTrue();
    Optional<ConsentDocument> findByTypeAndActiveTrue(ConsentDocumentType type);
}
