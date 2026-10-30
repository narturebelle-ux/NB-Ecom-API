package com.narturebelle.controller;

import com.narturebelle.dto.ChildResponse;
import com.narturebelle.dto.ChildVaccinationsResponse;
import com.narturebelle.dto.CreateChildRequest;
import com.narturebelle.dto.PatchChildRequest;
import com.narturebelle.dto.RecordVaccinationRequest;
import com.narturebelle.dto.VaccinationItemResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.ChildService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Child profiles + vaccination tracker (Phase 6, Workflow G). Owner-only; guardian sharing isn't built yet. */
@RestController
@RequestMapping("/api/v1/me/children")
public class ChildController {

    private final ChildService childService;
    private final AuthorizationService authorizationService;

    public ChildController(ChildService childService, AuthorizationService authorizationService) {
        this.childService = childService;
        this.authorizationService = authorizationService;
    }

    @RequireConsent
    @GetMapping
    public ResponseEntity<List<ChildResponse>> list() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(childService.listChildren(userId).stream().map(ChildResponse::from).toList());
    }

    @RequireConsent
    @PostMapping
    public ResponseEntity<ChildResponse> create(@Valid @RequestBody CreateChildRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(ChildResponse.from(childService.createChild(userId, req)));
    }

    @RequireConsent
    @PatchMapping("/{id}")
    public ResponseEntity<ChildResponse> patch(@PathVariable Long id, @Valid @RequestBody PatchChildRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(ChildResponse.from(childService.patchChild(userId, id, req)));
    }

    @RequireConsent
    @GetMapping("/{id}/vaccinations")
    public ResponseEntity<ChildVaccinationsResponse> vaccinations(@PathVariable Long id) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(childService.vaccinations(userId, id));
    }

    @RequireConsent
    @PutMapping("/{id}/vaccinations/{scheduleItemId}")
    public ResponseEntity<VaccinationItemResponse> recordVaccination(@PathVariable Long id, @PathVariable Long scheduleItemId,
                                                                     @Valid @RequestBody RecordVaccinationRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(childService.recordVaccination(userId, id, scheduleItemId, req));
    }

    @RequireConsent
    @DeleteMapping("/{id}/vaccinations/{scheduleItemId}")
    public ResponseEntity<Void> removeVaccination(@PathVariable Long id, @PathVariable Long scheduleItemId) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        childService.removeVaccination(userId, id, scheduleItemId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
