package com.narturebelle.controller;

import com.narturebelle.dto.UpdateFeatureFlagRequest;
import com.narturebelle.model.FeatureFlag;
import com.narturebelle.repository.FeatureFlagRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/** /api/v1/admin/** is already gated to the *_ADMIN roles by SecurityConfig — no extra check needed here. */
@RestController
@RequestMapping("/api/v1/admin/feature-flags")
public class AdminFeatureFlagController {

    private final FeatureFlagRepository featureFlagRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public AdminFeatureFlagController(FeatureFlagRepository featureFlagRepository,
                                       AuthorizationService authorizationService,
                                       AuditService auditService) {
        this.featureFlagRepository = featureFlagRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @PatchMapping("/{key}")
    public ResponseEntity<?> updateFlag(@PathVariable String key, @Valid @RequestBody UpdateFeatureFlagRequest req) {
        FeatureFlag flag = featureFlagRepository.findById(key).orElse(null);
        if (flag == null) {
            return ResponseEntity.notFound().build();
        }

        boolean previousValue = flag.isEnabled();
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();

        flag.setEnabled(req.getEnabled());
        flag.setUpdatedAt(LocalDateTime.now());
        flag.setUpdatedBy(principal.userId() != null ? String.valueOf(principal.userId()) : principal.firebaseUid());
        FeatureFlag saved = featureFlagRepository.save(flag);

        auditService.record("FEATURE_FLAG_UPDATED", "FeatureFlag", key, req.getReason(),
                Map.of("from", previousValue, "to", req.getEnabled()));

        return ResponseEntity.ok(saved);
    }
}
