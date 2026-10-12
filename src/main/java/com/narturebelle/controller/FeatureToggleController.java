package com.narturebelle.controller;

import com.narturebelle.config.FeatureToggleProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/features")
public class FeatureToggleController {

    private final FeatureToggleProperties featureToggleProperties;

    public FeatureToggleController(FeatureToggleProperties featureToggleProperties) {
        this.featureToggleProperties = featureToggleProperties;
    }

    @GetMapping
    public ResponseEntity<Map<String, Boolean>> getFeatures() {
        return ResponseEntity.ok(featureToggleProperties.toMap());
    }
}
