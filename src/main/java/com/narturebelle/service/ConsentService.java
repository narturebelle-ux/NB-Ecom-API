package com.narturebelle.service;

import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocument;
import com.narturebelle.repository.ConsentDocumentRepository;
import com.narturebelle.repository.ConsentRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConsentService {

    private final ConsentDocumentRepository consentDocumentRepository;
    private final ConsentRepository consentRepository;

    public ConsentService(ConsentDocumentRepository consentDocumentRepository, ConsentRepository consentRepository) {
        this.consentDocumentRepository = consentDocumentRepository;
        this.consentRepository = consentRepository;
    }

    /**
     * Required document types (TERMS, PRIVACY) with no active grant for the CURRENT active version.
     * userId == null (token valid, no user row yet) means nothing could possibly have been granted,
     * so every active document type is pending — no DB lookup needed for that case.
     */
    public List<String> pendingConsentTypes(Long userId) {
        List<ConsentDocument> activeDocuments = consentDocumentRepository.findByActiveTrue();

        if (userId == null) {
            return activeDocuments.stream().map(d -> d.getType().name()).sorted().toList();
        }

        return activeDocuments.stream()
                .filter(doc -> !consentRepository.existsByUserIdAndDocumentTypeAndDocumentVersionAndRevokedAtIsNull(
                        userId, doc.getType(), doc.getVersion()))
                .map(d -> d.getType().name())
                .sorted()
                .toList();
    }

    public List<Consent> history(Long userId) {
        return consentRepository.findByUserIdOrderByGrantedAtDesc(userId);
    }
}
