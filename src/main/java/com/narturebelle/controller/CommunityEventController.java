package com.narturebelle.controller;

import com.narturebelle.dto.CommunityEventResponse;
import com.narturebelle.dto.CommunityEventRsvpResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.CommunityEvent;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.CommunityEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Joy Circle (Phase 6). Event browsing is public (GET only, see SecurityConfig); RSVPs need a token + consent. */
@RestController
public class CommunityEventController {

    private final CommunityEventService communityEventService;
    private final AuthorizationService authorizationService;

    public CommunityEventController(CommunityEventService communityEventService, AuthorizationService authorizationService) {
        this.communityEventService = communityEventService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/api/v1/joy-circle/events")
    public ResponseEntity<List<CommunityEventResponse>> list(@RequestParam(defaultValue = "false") boolean includePast) {
        List<CommunityEventResponse> events = communityEventService.listEvents(includePast).stream()
                .map(this::toResponse).toList();
        return ResponseEntity.ok(events);
    }

    @GetMapping("/api/v1/joy-circle/events/{id}")
    public ResponseEntity<CommunityEventResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(communityEventService.getEvent(id)));
    }

    @RequireConsent
    @PostMapping("/api/v1/joy-circle/events/{id}/rsvp")
    public ResponseEntity<CommunityEventRsvpResponse> rsvp(@PathVariable Long id) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(communityEventService.rsvp(userId, id));
    }

    @RequireConsent
    @DeleteMapping("/api/v1/joy-circle/events/{id}/rsvp")
    public ResponseEntity<Void> cancelRsvp(@PathVariable Long id) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        communityEventService.cancelRsvp(userId, id);
        return ResponseEntity.noContent().build();
    }

    @RequireConsent
    @GetMapping("/api/v1/me/joy-circle/rsvps")
    public ResponseEntity<List<CommunityEventRsvpResponse>> myRsvps() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(communityEventService.listRsvps(userId));
    }

    private CommunityEventResponse toResponse(CommunityEvent event) {
        return CommunityEventResponse.from(event, communityEventService.rsvpCount(event.getId()));
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
