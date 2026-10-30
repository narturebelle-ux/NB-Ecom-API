package com.narturebelle.controller;

import com.narturebelle.dto.FeedbackRequest;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RateLimiter;
import com.narturebelle.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Public "ideas / feedback" form. A token is optional; when present, the sender's user id is attached. */
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final AuthorizationService authorizationService;

    public FeedbackController(FeedbackService feedbackService, AuthorizationService authorizationService) {
        this.feedbackService = feedbackService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody FeedbackRequest req, HttpServletRequest request) {
        if (req.getWebsite() != null && !req.getWebsite().isBlank()) {
            // Honeypot filled: a bot. Look successful, save nothing.
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", 0));
        }
        Long userId = authorizationService.currentPrincipalOrEmpty().map(FirebaseUserPrincipal::userId).orElse(null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", feedbackService.submit(req, userId, RateLimiter.clientIp(request)).getId()));
    }
}
