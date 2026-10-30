package com.narturebelle.controller;

import com.narturebelle.dto.CommunityEventResponse;
import com.narturebelle.dto.CreateCommunityEventRequest;
import com.narturebelle.model.CommunityEvent;
import com.narturebelle.service.CommunityEventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** /api/v1/admin/** is already role-gated by SecurityConfig (any *_ADMIN role) -- no extra check needed here. */
@RestController
@RequestMapping("/api/v1/admin/joy-circle/events")
public class AdminCommunityEventController {

    private final CommunityEventService communityEventService;

    public AdminCommunityEventController(CommunityEventService communityEventService) {
        this.communityEventService = communityEventService;
    }

    @PostMapping
    public ResponseEntity<CommunityEventResponse> create(@Valid @RequestBody CreateCommunityEventRequest req) {
        CommunityEvent event = communityEventService.createEvent(req);
        return ResponseEntity.ok(CommunityEventResponse.from(event, 0));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<CommunityEventResponse> cancel(@PathVariable Long id) {
        CommunityEvent event = communityEventService.cancelEvent(id);
        return ResponseEntity.ok(CommunityEventResponse.from(event, communityEventService.rsvpCount(id)));
    }
}
