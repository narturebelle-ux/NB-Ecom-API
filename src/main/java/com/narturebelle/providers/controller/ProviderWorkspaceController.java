package com.narturebelle.providers.controller;

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

    public ProviderWorkspaceController(ProviderWorkspaceService workspaceService, ProfileChangeService profileChangeService) {
        this.workspaceService = workspaceService;
        this.profileChangeService = profileChangeService;
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

    @PostMapping(value = "/change-requests/{id}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChangeRequestResponse> attachDocument(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(profileChangeService.attachDocument(id, file));
    }
}
