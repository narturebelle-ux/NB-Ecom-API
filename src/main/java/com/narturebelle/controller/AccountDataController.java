package com.narturebelle.controller;

import com.narturebelle.dto.CreateDeletionRequestRequest;
import com.narturebelle.dto.DeletionRequestResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.service.AccountDataService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Self-service data export and account deletion (Phase 6). Deliberately NOT @RequireConsent: a user who
 * has revoked consent must still be able to get their data out and ask for it to be deleted.
 */
@RestController
@RequestMapping("/api/v1/me")
public class AccountDataController {

    private final AccountDataService accountDataService;
    private final AuthorizationService authorizationService;

    public AccountDataController(AccountDataService accountDataService, AuthorizationService authorizationService) {
        this.accountDataService = accountDataService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        byte[] body = accountDataService.export(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("narturebelle-export-" + LocalDate.now() + ".json").build().toString())
                .body(body);
    }

    @GetMapping("/deletion-request")
    public ResponseEntity<DeletionRequestResponse> deletionRequest() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(DeletionRequestResponse.from(accountDataService.currentDeletionRequest(userId)));
    }

    @PostMapping("/deletion-request")
    public ResponseEntity<DeletionRequestResponse> requestDeletion(@Valid @RequestBody CreateDeletionRequestRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(DeletionRequestResponse.from(accountDataService.requestDeletion(userId)));
    }

    @DeleteMapping("/deletion-request")
    public ResponseEntity<Void> cancelDeletion() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        accountDataService.cancelDeletion(userId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
