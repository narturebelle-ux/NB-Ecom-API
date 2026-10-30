package com.narturebelle.service;

import com.narturebelle.exception.ConsentRequiredException;
import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocument;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.repository.ConsentDocumentRepository;
import com.narturebelle.repository.ConsentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ConsentService {

    private final ConsentDocumentRepository consentDocumentRepository;
    private final ConsentRepository consentRepository;

    public ConsentService(ConsentDocumentRepository consentDocumentRepository, ConsentRepository consentRepository) {
        this.consentDocumentRepository = consentDocumentRepository;
        this.consentRepository = consentRepository;
    }

    /**
     * Required document types (TERMS, PRIVACY -- see ConsentDocumentType.isRequired) with no active grant for the CURRENT active version.
     * userId == null (token valid, no user row yet) means nothing could possibly have been granted,
     * so every active document type is pending — no DB lookup needed for that case.
     */
    public List<String> pendingConsentTypes(Long userId) {
        List<ConsentDocument> activeDocuments = consentDocumentRepository.findByActiveTrue().stream()
                .filter(doc -> doc.getType().isRequired())
                .toList();

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

    /**
     * Feature-level gate for a non-required consent type: 403 CONSENT_REQUIRED (pending = [type]) unless
     * the user has an active grant for the CURRENT version. No active document of that type means there's
     * nothing to consent to yet, so nothing is blocked.
     */
    public void requireGranted(Long userId, ConsentDocumentType type) {
        if (consentDocumentRepository.findByTypeAndActiveTrue(type).isPresent() && !isGranted(userId, type)) {
            throw new ConsentRequiredException(List.of(type.name()));
        }
    }

    /** Strict: true only if the user holds an unrevoked grant of the current active version. No document = not granted. */
    public boolean isGranted(Long userId, ConsentDocumentType type) {
        Optional<ConsentDocument> active = consentDocumentRepository.findByTypeAndActiveTrue(type);
        return active.isPresent() && userId != null
                && consentRepository.existsByUserIdAndDocumentTypeAndDocumentVersionAndRevokedAtIsNull(
                        userId, type, active.get().getVersion());
    }

    /** Any unrevoked grant of this type, whatever its version. Used where an older version's wording still covers the use. */
    public boolean hasAnyGrant(Long userId, ConsentDocumentType type) {
        return userId != null && consentRepository.existsByUserIdAndDocumentTypeAndRevokedAtIsNull(userId, type);
    }

    public List<Consent> history(Long userId) {
        return consentRepository.findByUserIdOrderByGrantedAtDesc(userId);
    }
}
