package com.narturebelle.controller;

import com.narturebelle.dto.CreateIdeaRequest;
import com.narturebelle.dto.IdeaResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.IdeaCategory;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.IdeaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Idea pool. Browsing (GET /ideas/**) is public so a shared link works for anyone; a token, when sent,
 * adds votedByMe/mine. Submitting and voting need a signed-in member.
 */
@RestController
@RequestMapping("/api/v1")
public class IdeaController {

    private final IdeaService ideaService;
    private final AuthorizationService authorizationService;

    public IdeaController(IdeaService ideaService, AuthorizationService authorizationService) {
        this.ideaService = ideaService;
        this.authorizationService = authorizationService;
    }

    /** Categories, the vote goal and whether the pool is open -- everything the landing section needs. */
    @GetMapping("/ideas/meta")
    public ResponseEntity<Map<String, Object>> meta() {
        List<Map<String, String>> categories = Arrays.stream(IdeaCategory.values())
                .map(c -> Map.of("code", c.name(), "label", c.getLabel(), "description", c.getDescription()))
                .toList();
        return ResponseEntity.ok(Map.of("open", ideaService.poolOpen(), "voteGoal", ideaService.voteGoal(),
                "categories", categories));
    }

    @GetMapping("/ideas")
    public ResponseEntity<List<IdeaResponse>> pool(@RequestParam(required = false) IdeaCategory category,
                                                   @RequestParam(defaultValue = "top") String sort) {
        return ResponseEntity.ok(ideaService.pool(category, sort, viewerId()));
    }

    @GetMapping("/ideas/impact")
    public ResponseEntity<List<IdeaResponse>> impact() {
        return ResponseEntity.ok(ideaService.impact());
    }

    @GetMapping("/ideas/{id}")
    public ResponseEntity<IdeaResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(ideaService.get(id, viewerId()));
    }

    @RequireConsent
    @PostMapping("/ideas/{id}/vote")
    public ResponseEntity<IdeaResponse> vote(@PathVariable Long id) {
        return ResponseEntity.ok(ideaService.vote(id, requireUserId()));
    }

    @RequireConsent
    @DeleteMapping("/ideas/{id}/vote")
    public ResponseEntity<IdeaResponse> unvote(@PathVariable Long id) {
        return ResponseEntity.ok(ideaService.unvote(id, requireUserId()));
    }

    @RequireConsent
    @PostMapping("/me/ideas")
    public ResponseEntity<IdeaResponse> submit(@Valid @RequestBody CreateIdeaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ideaService.submit(requireUserId(), req));
    }

    @RequireConsent
    @GetMapping("/me/ideas")
    public ResponseEntity<List<IdeaResponse>> mine() {
        return ResponseEntity.ok(ideaService.mine(requireUserId()));
    }

    private Long viewerId() {
        return authorizationService.currentPrincipalOrEmpty().map(FirebaseUserPrincipal::userId).orElse(null);
    }

    private Long requireUserId() {
        Long userId = authorizationService.currentPrincipal().userId();
        if (userId == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return userId;
    }
}
