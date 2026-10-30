package com.narturebelle.controller;

import com.narturebelle.model.FeatureFlag;
import com.narturebelle.repository.FeatureFlagRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/features")
public class FeatureToggleController {

    private final FeatureFlagRepository featureFlagRepository;

    public FeatureToggleController(FeatureFlagRepository featureFlagRepository) {
        this.featureFlagRepository = featureFlagRepository;
    }

    /** Same flat {key: boolean} shape as before — now backed by the feature_flags table, not YAML. */
    @GetMapping
    public ResponseEntity<Map<String, Boolean>> getFeatures() {
        Map<String, Boolean> flags = featureFlagRepository.findAll().stream()
                .collect(Collectors.toMap(FeatureFlag::getKey, FeatureFlag::isEnabled));
        return ResponseEntity.ok(flags);
    }
}
