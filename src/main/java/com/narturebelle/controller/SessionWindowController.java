package com.narturebelle.controller;

import com.narturebelle.dto.SessionWindowsResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.SessionWindowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Suggested date ranges for each professional's sessions, from her due date (general guidance). */
@RestController
@RequestMapping("/api/v1/me/care/session-windows")
public class SessionWindowController {

    private final SessionWindowService sessionWindowService;
    private final AuthorizationService authorizationService;

    public SessionWindowController(SessionWindowService sessionWindowService, AuthorizationService authorizationService) {
        this.sessionWindowService = sessionWindowService;
        this.authorizationService = authorizationService;
    }

    @RequireConsent
    @GetMapping
    public ResponseEntity<SessionWindowsResponse> windows() {
        Long userId = authorizationService.currentPrincipal().userId();
        if (userId == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return ResponseEntity.ok(sessionWindowService.windowsFor(userId));
    }
}
