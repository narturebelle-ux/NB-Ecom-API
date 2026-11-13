package com.narturebelle.providers.controller;

import com.narturebelle.dto.MultipartUploadRequests;
import com.narturebelle.storage.SignedDownloadUrl;
import com.narturebelle.providers.dto.ReviewDtos.BankVerificationRequest;
import com.narturebelle.providers.dto.ReviewDtos.ChangeDecisionRequest;
import com.narturebelle.providers.dto.ReviewDtos.ChangeRequestForReview;
import com.narturebelle.providers.dto.ReviewDtos.MedicalDecisionRequest;
import com.narturebelle.providers.dto.ReviewDtos.OperationsDecisionRequest;
import com.narturebelle.providers.dto.ReviewDtos.QueueItem;
import com.narturebelle.providers.dto.ReviewDtos.ReviewDetail;
import com.narturebelle.providers.dto.ReviewDtos.StatusChangeRequest;
import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.providers.service.ApplicationReviewService;
import com.narturebelle.providers.service.ProfileChangeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** /api/v1/admin/** is limited to Operations and Medical Reviewers by SecurityConfig; the service checks each stage's own role. */
@RestController
@RequestMapping("/api/v1/admin/provider-applications")
public class AdminApplicationController {

    private final ApplicationReviewService reviewService;
    private final ProfileChangeService profileChangeService;

    public AdminApplicationController(ApplicationReviewService reviewService, ProfileChangeService profileChangeService) {
        this.reviewService = reviewService;
        this.profileChangeService = profileChangeService;
    }

    @GetMapping
    public ResponseEntity<List<QueueItem>> queue(@RequestParam(required = false) List<ApplicationStatus> status) {
        return ResponseEntity.ok(reviewService.queue(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReviewDetail> detail(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.detail(id));
    }

    @GetMapping("/documents/{documentId}/file")
    public ResponseEntity<byte[]> document(@PathVariable Long documentId) {
        return ApplicationController.attachment(reviewService.document(documentId));
    }

    /** An S3 document as a 10-minute link, audited like /file. 409 FILE_NOT_READY / FILE_BLOCKED; older Postgres files 404. */
    @PostMapping("/documents/{documentId}/download-url")
    public ResponseEntity<SignedDownloadUrl> documentDownloadUrl(@PathVariable Long documentId,
                                                                 @RequestBody(required = false) MultipartUploadRequests.Download body) {
        return ResponseEntity.ok(reviewService.documentDownloadUrl(documentId, body == null ? null : body.getDisposition()));
    }

    @PostMapping("/{id}/operations-decision")
    public ResponseEntity<ReviewDetail> operationsDecision(@PathVariable Long id, @Valid @RequestBody OperationsDecisionRequest req) {
        reviewService.operationsDecision(id, req);
        return ResponseEntity.ok(reviewService.detail(id));
    }

    @PostMapping("/{id}/medical-decision")
    public ResponseEntity<ReviewDetail> medicalDecision(@PathVariable Long id, @Valid @RequestBody MedicalDecisionRequest req) {
        reviewService.medicalDecision(id, req);
        return ResponseEntity.ok(reviewService.detail(id));
    }

    @PostMapping("/{id}/bank-verification")
    public ResponseEntity<ReviewDetail> verifyBank(@PathVariable Long id, @Valid @RequestBody BankVerificationRequest req) {
        reviewService.verifyBank(id, req.passed(), req.reason());
        return ResponseEntity.ok(reviewService.detail(id));
    }

    @PostMapping("/{id}/recheck-complete")
    public ResponseEntity<ReviewDetail> recheckComplete(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        reviewService.completeRecheck(id, body == null ? null : body.get("reason"));
        return ResponseEntity.ok(reviewService.detail(id));
    }

    @PostMapping("/{id}/go-live")
    public ResponseEntity<ReviewDetail> goLive(@PathVariable Long id) {
        reviewService.goLive(id);
        return ResponseEntity.ok(reviewService.detail(id));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ReviewDetail> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req) {
        reviewService.changeStatus(id, req.status(), req.reason());
        return ResponseEntity.ok(reviewService.detail(id));
    }

    @GetMapping("/change-requests")
    public ResponseEntity<List<ChangeRequestForReview>> pendingChanges() {
        return ResponseEntity.ok(profileChangeService.pendingForReview());
    }

    @PostMapping("/change-requests/{id}/decision")
    public ResponseEntity<ChangeRequestForReview> decideChange(@PathVariable Long id, @Valid @RequestBody ChangeDecisionRequest req) {
        return ResponseEntity.ok(profileChangeService.decide(id, req.approve(), req.reason()));
    }
}
