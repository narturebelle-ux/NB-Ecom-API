package com.narturebelle.controller;

import com.narturebelle.dto.CoordinatorResponse;
import com.narturebelle.dto.CreateCoordinatorRequest;
import com.narturebelle.dto.UpdateCoordinatorRequest;
import com.narturebelle.service.CoordinatorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** /api/v1/admin/** is already role-gated by SecurityConfig (any *_ADMIN role) -- no extra check needed here. */
@RestController
@RequestMapping("/api/v1/admin/coordinators")
public class AdminCoordinatorController {

    private final CoordinatorService coordinatorService;

    public AdminCoordinatorController(CoordinatorService coordinatorService) {
        this.coordinatorService = coordinatorService;
    }

    @GetMapping
    public ResponseEntity<List<CoordinatorResponse>> list() {
        return ResponseEntity.ok(coordinatorService.list().stream().map(CoordinatorResponse::forAdmin).toList());
    }

    @PostMapping
    public ResponseEntity<CoordinatorResponse> create(@Valid @RequestBody CreateCoordinatorRequest req) {
        return ResponseEntity.ok(CoordinatorResponse.forAdmin(coordinatorService.create(req)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CoordinatorResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateCoordinatorRequest req) {
        return ResponseEntity.ok(CoordinatorResponse.forAdmin(coordinatorService.setActive(id, req.getActive())));
    }
}
