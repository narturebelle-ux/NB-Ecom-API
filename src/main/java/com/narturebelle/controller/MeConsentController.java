package com.narturebelle.controller;

import com.narturebelle.dto.GrantConsentRequest;
import com.narturebelle.exception.ConsentVersionOutdatedException;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocument;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.repository.ConsentDocumentRepository;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.service.ConsentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/me/consents")
public class MeConsentController {

    private final ConsentDocumentRepository consentDocumentRepository;
    private final ConsentRepository consentRepository;
    private final ConsentService consentService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public MeConsentController(ConsentDocumentRepository consentDocumentRepository, ConsentRepository consentRepository,
                                ConsentService consentService, AuthorizationService authorizationService,
                                AuditService auditService) {
        this.consentDocumentRepository = consentDocumentRepository;
        this.consentRepository = consentRepository;
        this.consentService = consentService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /** Not @RequireConsent-gated — this is how the user sees what's pending in the first place. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConsents() {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        Long userId = principal.userId();

        return ResponseEntity.ok(Map.of(
                "history", userId == null ? List.of() : consentService.history(userId),
                "pending", consentService.pendingConsentTypes(userId)
        ));
    }

    @PostMapping
    public ResponseEntity<Consent> grant(@Valid @RequestBody GrantConsentRequest req) {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        requireUserId(principal);

        ConsentDocument active = consentDocumentRepository.findByTypeAndActiveTrue(req.getDocumentType())
                .orElseThrow(() -> new ConsentVersionOutdatedException(
                        "No active " + req.getDocumentType() + " document exists."));

        if (!active.getVersion().equals(req.getDocumentVersion())) {
            throw new ConsentVersionOutdatedException(
                    "Version " + req.getDocumentVersion() + " of " + req.getDocumentType()
                            + " is outdated — current version is " + active.getVersion() + ".");
        }

        Consent consent = new Consent();
        consent.setUserId(principal.userId());
        consent.setDocumentType(req.getDocumentType());
        consent.setDocumentVersion(req.getDocumentVersion());
        Consent saved = consentRepository.save(consent);

        auditService.record("CONSENT_GRANTED", "Consent", String.valueOf(saved.getId()), null,
                Map.of("documentType", req.getDocumentType().name(), "documentVersion", req.getDocumentVersion()));

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{type}/revoke")
    public ResponseEntity<?> revoke(@PathVariable String type) {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        requireUserId(principal);

        ConsentDocumentType documentType;
        try {
            documentType = ConsentDocumentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "VALIDATION_FAILED",
                    "message", "Unknown consent document type: " + type));
        }

        Optional<Consent> active = consentRepository
                .findFirstByUserIdAndDocumentTypeAndRevokedAtIsNullOrderByGrantedAtDesc(principal.userId(), documentType);
        if (active.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Consent consent = active.get();
        consent.setRevokedAt(LocalDateTime.now());
        Consent saved = consentRepository.save(consent);

        auditService.record("CONSENT_REVOKED", "Consent", String.valueOf(saved.getId()), null,
                Map.of("documentType", documentType.name(), "documentVersion", saved.getDocumentVersion()));

        return ResponseEntity.ok(saved);
    }

    private void requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
    }
}
