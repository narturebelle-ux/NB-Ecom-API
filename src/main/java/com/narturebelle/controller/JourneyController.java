package com.narturebelle.controller;

import com.narturebelle.dto.CreateJourneyRequest;
import com.narturebelle.dto.JourneyResponse;
import com.narturebelle.dto.PatchJourneyRequest;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.Journey;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.JourneyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me/journeys")
public class JourneyController {

    private final JourneyService journeyService;
    private final AuthorizationService authorizationService;

    public JourneyController(JourneyService journeyService, AuthorizationService authorizationService) {
        this.journeyService = journeyService;
        this.authorizationService = authorizationService;
    }

    @RequireConsent
    @PostMapping
    public ResponseEntity<JourneyResponse> create(@Valid @RequestBody CreateJourneyRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        Journey journey = journeyService.create(userId, req);
        return ResponseEntity.ok(JourneyResponse.from(journey));
    }

    @RequireConsent
    @GetMapping
    public ResponseEntity<List<JourneyResponse>> list() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        List<JourneyResponse> journeys = journeyService.listForUser(userId).stream()
                .map(JourneyResponse::from)
                .toList();
        return ResponseEntity.ok(journeys);
    }

    @RequireConsent
    @GetMapping("/{id}")
    public ResponseEntity<JourneyResponse> getOne(@PathVariable Long id) {
        Journey journey = journeyService.find(id).orElse(null);
        if (journey == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireSelfOrAdmin(journey.getUserId());
        return ResponseEntity.ok(JourneyResponse.from(journey));
    }

    @RequireConsent
    @PatchMapping("/{id}")
    public ResponseEntity<JourneyResponse> update(@PathVariable Long id, @RequestBody PatchJourneyRequest req) {
        Journey journey = journeyService.find(id).orElse(null);
        if (journey == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireSelfOrAdmin(journey.getUserId());
        Journey saved = journeyService.update(journey, req);
        return ResponseEntity.ok(JourneyResponse.from(saved));
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
