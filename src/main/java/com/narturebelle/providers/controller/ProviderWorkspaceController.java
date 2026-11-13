package com.narturebelle.providers.controller;

import com.narturebelle.dto.DirectUploadRequests;
import com.narturebelle.dto.MultipartUploadRequests;
import com.narturebelle.model.UploadPurpose;
import com.narturebelle.providers.exception.ProviderOnboardingException;
import com.narturebelle.providers.service.ApplicationDocumentService;
import com.narturebelle.security.RateLimiter;
import com.narturebelle.storage.DirectUploadService;
import com.narturebelle.storage.UploadedPart;
import java.time.Duration;
import java.util.Map;
import com.narturebelle.providers.dto.WorkspaceDtos.ChangeRequestBody;
import com.narturebelle.providers.dto.WorkspaceDtos.ChangeRequestResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.EarningsResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.FeedbackResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.ProductActivityItem;
import com.narturebelle.providers.dto.WorkspaceDtos.ProfileResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.ServiceRequest;
import com.narturebelle.providers.dto.WorkspaceDtos.ServiceResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.SlotResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.SlotsRequest;
import com.narturebelle.providers.service.ProfileChangeService;
import com.narturebelle.providers.service.ProviderWorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** The professional's dashboard. Access rules are in ProviderWorkspaceService (approved application only). */
@RestController
@RequestMapping("/api/v1/provider-workspace")
public class ProviderWorkspaceController {

    private final ProviderWorkspaceService workspaceService;
    private final ProfileChangeService profileChangeService;
    private final DirectUploadService directUploads;
    private final RateLimiter rateLimiter;

    public ProviderWorkspaceController(ProviderWorkspaceService workspaceService, ProfileChangeService profileChangeService,
                                       DirectUploadService directUploads, RateLimiter rateLimiter) {
        this.workspaceService = workspaceService;
        this.profileChangeService = profileChangeService;
        this.directUploads = directUploads;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/services")
    public ResponseEntity<List<ServiceResponse>> services() {
        return ResponseEntity.ok(workspaceService.services());
    }

    @PostMapping("/services")
    public ResponseEntity<ServiceResponse> addService(@Valid @RequestBody ServiceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceService.addService(req));
    }

    @PatchMapping("/services/{id}")
    public ResponseEntity<ServiceResponse> setServiceActive(@PathVariable Long id, @RequestParam boolean active) {
        return ResponseEntity.ok(workspaceService.setServiceActive(id, active));
    }

    @GetMapping("/slots")
    public ResponseEntity<List<SlotResponse>> slots() {
        return ResponseEntity.ok(workspaceService.slots());
    }

    @PostMapping("/slots")
    public ResponseEntity<List<SlotResponse>> addSlots(@Valid @RequestBody SlotsRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceService.addSlots(req.slots()));
    }

    @DeleteMapping("/slots/{id}")
    public ResponseEntity<Void> removeSlot(@PathVariable Long id) {
        workspaceService.removeSlot(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/earnings")
    public ResponseEntity<EarningsResponse> earnings() {
        return ResponseEntity.ok(workspaceService.earnings());
    }

    @GetMapping("/feedback")
    public ResponseEntity<FeedbackResponse> feedback() {
        return ResponseEntity.ok(workspaceService.feedback());
    }

    @GetMapping("/product-activity")
    public ResponseEntity<List<ProductActivityItem>> productActivity() {
        return ResponseEntity.ok(workspaceService.productActivity());
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> profile() {
        return ResponseEntity.ok(workspaceService.profile());
    }

    @GetMapping("/change-requests")
    public ResponseEntity<List<ChangeRequestResponse>> changeRequests() {
        return ResponseEntity.ok(profileChangeService.mine());
    }

    @PostMapping("/change-requests")
    public ResponseEntity<ChangeRequestResponse> requestChange(@Valid @RequestBody ChangeRequestBody req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileChangeService.request(req.type(), req.values()));
    }

    /** The older upload through the API into Postgres (4 MB). Kept while the UI moves to the S3 upload. */
    @PostMapping(value = "/change-requests/{id}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChangeRequestResponse> attachDocument(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(profileChangeService.attachDocument(id, file));
    }

    /** Saves a document the browser has uploaded straight to S3 (POST /provider-workspace/files/upload-url first). */
    @PostMapping(value = "/change-requests/{id}/document", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChangeRequestResponse> attachUploadedDocument(@PathVariable Long id,
                                                                        @Valid @RequestBody DirectUploadRequests.SavedFile file) {
        return ResponseEntity.ok(profileChangeService.attachUploadedDocument(id, file));
    }

    // ---- direct-to-S3 upload steps (same shapes as /records §10a); 404 DIRECT_UPLOAD_UNAVAILABLE without S3 ----

    @GetMapping("/files/upload-rules")
    public ResponseEntity<DirectUploadService.UploadRules> uploadRules(@RequestParam Long changeRequestId) {
        return ResponseEntity.ok(profileChangeService.documentUploadRules(changeRequestId));
    }

    @PostMapping("/files/upload-url")
    public ResponseEntity<DirectUploadService.UploadUrl> uploadUrl(@Valid @RequestBody DirectUploadRequests.UploadUrl req) {
        Long userId = profileChangeService.requireApprovedUserId();
        if (!rateLimiter.tryAcquire("provider-upload:" + userId, 60, Duration.ofHours(1))) {
            throw new ProviderOnboardingException(429, "TOO_MANY_REQUESTS", "Too many uploads. Please try again later.");
        }
        return ResponseEntity.ok(profileChangeService.documentUploadUrl(req));
    }

    @PostMapping("/files/uploads/{uploadId}/part-urls")
    public ResponseEntity<Map<String, Object>> partUrls(@PathVariable String uploadId,
                                                        @Valid @RequestBody MultipartUploadRequests.PartUrls req) {
        var parts = directUploads.partUrls(ownerScope(), uploadId, req.getObjectPath(), req.getPartNumbers());
        return ResponseEntity.ok(Map.of("parts", parts));
    }

    @PostMapping("/files/uploads/{uploadId}/complete")
    public ResponseEntity<Void> completeUpload(@PathVariable String uploadId, @Valid @RequestBody MultipartUploadRequests.Complete req) {
        List<UploadedPart> parts = req.getParts().stream().map(p -> new UploadedPart(p.getPartNumber(), p.getEtag())).toList();
        directUploads.complete(ownerScope(), uploadId, req.getObjectPath(), parts);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/files/uploads/{uploadId}")
    public ResponseEntity<Void> abortUpload(@PathVariable String uploadId, @RequestParam String objectPath) {
        directUploads.abort(ownerScope(), uploadId, objectPath);
        return ResponseEntity.noContent().build();
    }

    private DirectUploadService.UploadScope ownerScope() {
        return ApplicationDocumentService.ownerScope(UploadPurpose.PROVIDER_CHANGE_REQUEST, profileChangeService.requireApprovedUserId());
    }
}
