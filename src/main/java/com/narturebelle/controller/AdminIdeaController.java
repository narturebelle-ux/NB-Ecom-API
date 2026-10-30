package com.narturebelle.controller;

import com.narturebelle.dto.IdeaResponse;
import com.narturebelle.dto.IdeaStatusRequest;
import com.narturebelle.model.IdeaStatus;
import com.narturebelle.service.IdeaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** /api/v1/admin/** is already role-gated by SecurityConfig (any *_ADMIN role) -- no extra check needed here.
 * Opening/closing the pool itself is the ideaPool feature flag (PUT /admin/feature-flags/ideaPool). */
@RestController
@RequestMapping("/api/v1/admin/ideas")
public class AdminIdeaController {

    private final IdeaService ideaService;

    public AdminIdeaController(IdeaService ideaService) {
        this.ideaService = ideaService;
    }

    @GetMapping
    public ResponseEntity<List<IdeaResponse>> list(@RequestParam(required = false) IdeaStatus status) {
        return ResponseEntity.ok(ideaService.all(status));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<IdeaResponse> setStatus(@PathVariable Long id, @Valid @RequestBody IdeaStatusRequest req) {
        return ResponseEntity.ok(ideaService.setStatus(id, req));
    }
}
