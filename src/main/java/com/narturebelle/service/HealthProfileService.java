package com.narturebelle.service;

import com.narturebelle.exception.HealthProfileNotFoundException;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.HealthProfile;
import com.narturebelle.model.RiskFactor;
import com.narturebelle.repository.HealthProfileRepository;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The user's health details (see HealthProfile). Writing needs the HEALTH_DATA consent; reading
 * her own profile doesn't, so she can always see what's held. Every read and write is audited --
 * that it happened, never what's in it.
 */
@Service
public class HealthProfileService {

    private final HealthProfileRepository healthProfileRepository;
    private final ConsentService consentService;
    private final AuditService auditService;

    public HealthProfileService(HealthProfileRepository healthProfileRepository, ConsentService consentService,
                                AuditService auditService) {
        this.healthProfileRepository = healthProfileRepository;
        this.consentService = consentService;
        this.auditService = auditService;
    }

    public HealthProfile get(Long userId) {
        HealthProfile profile = healthProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new HealthProfileNotFoundException("You haven't shared any health details yet."));
        auditService.record("HEALTH_PROFILE_VIEWED", "HEALTH_PROFILE", String.valueOf(profile.getId()), "User viewed their health profile");
        return profile;
    }

    /** Full replacement. 403 CONSENT_REQUIRED (pending ["HEALTH_DATA"]) without the consent. */
    public HealthProfile save(Long userId, Boolean firstPregnancy, Boolean multiples, Set<RiskFactor> riskFactors) {
        consentService.requireGranted(userId, ConsentDocumentType.HEALTH_DATA);
        HealthProfile profile = healthProfileRepository.findByUserId(userId).orElseGet(() -> {
            HealthProfile fresh = new HealthProfile();
            fresh.setUserId(userId);
            return fresh;
        });
        profile.setFirstPregnancy(firstPregnancy);
        profile.setMultiples(multiples);
        profile.setRiskFactors(new HashSet<>(riskFactors != null ? riskFactors : Set.of()));
        HealthProfile saved = healthProfileRepository.save(profile);
        auditService.record("HEALTH_PROFILE_UPDATED", "HEALTH_PROFILE", String.valueOf(saved.getId()), "User updated their health profile");
        return saved;
    }

    /**
     * For product recommendations: only when the HEALTH_DATA consent is currently granted -- revoking it
     * stops her health details being used, even though the saved profile stays hers to see or delete.
     * Not audited per call; recommendations never return or log the profile itself.
     */
    public Optional<HealthProfile> forRecommendations(Long userId) {
        if (!consentService.isGranted(userId, ConsentDocumentType.HEALTH_DATA)) {
            return Optional.empty();
        }
        return healthProfileRepository.findByUserId(userId);
    }
}
