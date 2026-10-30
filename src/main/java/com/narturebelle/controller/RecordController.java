package com.narturebelle.controller;

import com.narturebelle.dto.CreateRecordShareRequest;
import com.narturebelle.dto.CreateRecordUploadUrlRequest;
import com.narturebelle.dto.HealthRecordResponse;
import com.narturebelle.dto.PatchRecordRequest;
import com.narturebelle.dto.RecordAccessLogEntryResponse;
import com.narturebelle.dto.RecordDownloadUrlResponse;
import com.narturebelle.dto.RecordShareResponse;
import com.narturebelle.dto.RecordUploadUrlResponse;
import com.narturebelle.dto.RegisterRecordRequest;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.RecordShare;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireVerified;
import com.narturebelle.service.ConsentService;
import com.narturebelle.service.ProviderService;
import com.narturebelle.service.RecordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/records")
public class RecordController {

    private final RecordService recordService;
    private final ProviderService providerService;
    private final AuthorizationService authorizationService;
    private final ConsentService consentService;

    public RecordController(RecordService recordService, ProviderService providerService,
                             AuthorizationService authorizationService, ConsentService consentService) {
        this.recordService = recordService;
        this.providerService = providerService;
        this.authorizationService = authorizationService;
        this.consentService = consentService;
    }

    /** Both upload steps need the RECORDS_STORAGE consent (403 CONSENT_REQUIRED, pending ["RECORDS_STORAGE"]). */
    @RequireVerified
    @PostMapping("/upload-url")
    public ResponseEntity<RecordUploadUrlResponse> uploadUrl(@Valid @RequestBody CreateRecordUploadUrlRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        consentService.requireGranted(userId, ConsentDocumentType.RECORDS_STORAGE);
        var intent = recordService.issueUploadUrl(userId, req);
        return ResponseEntity.ok(RecordUploadUrlResponse.of(intent.objectPath(), intent.uploadUrl(), intent.token(), intent.expiresAt()));
    }

    @RequireVerified
    @PostMapping
    public ResponseEntity<HealthRecordResponse> register(@Valid @RequestBody RegisterRecordRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        consentService.requireGranted(userId, ConsentDocumentType.RECORDS_STORAGE);
        HealthRecord record = recordService.registerUpload(userId, req);
        return ResponseEntity.ok(HealthRecordResponse.from(record));
    }

    @GetMapping
    public ResponseEntity<List<HealthRecordResponse>> list() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        List<HealthRecordResponse> records = recordService.listForOwner(userId).stream()
                .map(HealthRecordResponse::from).toList();
        return ResponseEntity.ok(records);
    }

    @GetMapping("/shared-with-me")
    public ResponseEntity<List<HealthRecordResponse>> sharedWithMe() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        var callerProvider = providerService.findByUserId(userId).orElse(null);
        List<HealthRecordResponse> records = callerProvider != null
                ? recordService.listSharedWithProvider(callerProvider.getId()).stream().map(HealthRecordResponse::from).toList()
                : List.of();
        return ResponseEntity.ok(records);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HealthRecordResponse> getOne(@PathVariable Long id) {
        HealthRecord record = recordService.find(id).orElse(null);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        var access = authorizationService.requireRecordAccess(record);
        recordService.logView(record, authorizationService.currentPrincipal().userId(), access.relationship(), access.matchedShareId());
        return ResponseEntity.ok(HealthRecordResponse.from(record));
    }

    @RequireVerified
    @PatchMapping("/{id}")
    public ResponseEntity<HealthRecordResponse> patch(@PathVariable Long id, @Valid @RequestBody PatchRecordRequest req) {
        HealthRecord record = recordService.find(id).orElse(null);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireSelfOrAdmin(record.getUserId());
        Long actorUserId = authorizationService.currentPrincipal().userId();
        HealthRecord saved = recordService.patchCategory(record, req.getCategory(), actorUserId);
        return ResponseEntity.ok(HealthRecordResponse.from(saved));
    }

    @RequireVerified
    @DeleteMapping("/{id}")
    public ResponseEntity<HealthRecordResponse> delete(@PathVariable Long id) {
        HealthRecord record = recordService.find(id).orElse(null);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireSelfOrAdmin(record.getUserId());
        Long actorUserId = authorizationService.currentPrincipal().userId();
        HealthRecord saved = recordService.softDelete(record, actorUserId);
        return ResponseEntity.ok(HealthRecordResponse.from(saved));
    }

    @RequireVerified
    @PostMapping("/{id}/download-url")
    public ResponseEntity<RecordDownloadUrlResponse> downloadUrl(@PathVariable Long id) {
        HealthRecord record = recordService.find(id).orElse(null);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        var access = authorizationService.requireRecordAccess(record);
        Long actorUserId = authorizationService.currentPrincipal().userId();
        var intent = recordService.issueDownloadUrl(record, actorUserId, access.relationship(), access.matchedShareId());
        return ResponseEntity.ok(RecordDownloadUrlResponse.of(intent.downloadUrl(), intent.expiresAt()));
    }

    @RequireVerified
    @PostMapping("/{id}/shares")
    public ResponseEntity<RecordShareResponse> createShare(@PathVariable Long id, @Valid @RequestBody CreateRecordShareRequest req) {
        HealthRecord record = recordService.find(id).orElse(null);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireSelfOrAdmin(record.getUserId());
        Long actorUserId = authorizationService.currentPrincipal().userId();
        RecordShare share = recordService.createShare(record, actorUserId, req);
        return ResponseEntity.ok(RecordShareResponse.from(share));
    }

    @GetMapping("/{id}/shares")
    public ResponseEntity<List<RecordShareResponse>> listShares(@PathVariable Long id) {
        HealthRecord record = recordService.find(id).orElse(null);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireSelfOrAdmin(record.getUserId());
        List<RecordShareResponse> shares = recordService.listShares(id).stream().map(RecordShareResponse::from).toList();
        return ResponseEntity.ok(shares);
    }

    @RequireVerified
    @PostMapping("/shares/{shareId}/revoke")
    public ResponseEntity<RecordShareResponse> revokeShare(@PathVariable Long shareId, @RequestBody java.util.Map<String, String> body) {
        RecordShare share = recordService.findShare(shareId).orElse(null);
        if (share == null) {
            return ResponseEntity.notFound().build();
        }
        HealthRecord record = recordService.find(share.getRecordId()).orElse(null);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireSelfOrAdmin(record.getUserId());
        Long actorUserId = authorizationService.currentPrincipal().userId();
        String reason = body.getOrDefault("reason", null);
        RecordShare saved = recordService.revokeShare(share, actorUserId, reason);
        return ResponseEntity.ok(RecordShareResponse.from(saved));
    }

    @GetMapping("/{id}/access-log")
    public ResponseEntity<List<RecordAccessLogEntryResponse>> accessLog(@PathVariable Long id) {
        HealthRecord record = recordService.find(id).orElse(null);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        boolean isAdmin = principal.isAdmin();
        boolean isOwner = principal.userId() != null && principal.userId().equals(record.getUserId());
        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("Only the record's owner or an admin may view its access log.");
        }
        List<RecordAccessLogEntryResponse> log = recordService.listAccessLog(id).stream()
                .map(event -> RecordAccessLogEntryResponse.from(event, record))
                .toList();
        return ResponseEntity.ok(log);
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
