package com.narturebelle.controller;

import com.narturebelle.dto.DeletionRequestResponse;
import com.narturebelle.dto.UpdateDeletionRequestStatusRequest;
import com.narturebelle.service.AccountDataService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** /api/v1/admin/** is already role-gated by SecurityConfig (any *_ADMIN role) -- no extra check needed here. */
@RestController
@RequestMapping("/api/v1/admin/deletion-requests")
public class AdminDeletionRequestController {

    private final AccountDataService accountDataService;

    public AdminDeletionRequestController(AccountDataService accountDataService) {
        this.accountDataService = accountDataService;
    }

    @GetMapping
    public ResponseEntity<List<DeletionRequestResponse>> list() {
        return ResponseEntity.ok(accountDataService.listDeletionRequests().stream().map(DeletionRequestResponse::forAdmin).toList());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DeletionRequestResponse> updateStatus(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateDeletionRequestStatusRequest req) {
        return ResponseEntity.ok(DeletionRequestResponse.forAdmin(accountDataService.updateDeletionStatus(id, req.getStatus())));
    }
}
