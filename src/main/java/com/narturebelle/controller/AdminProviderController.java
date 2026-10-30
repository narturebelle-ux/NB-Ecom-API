package com.narturebelle.controller;

import com.narturebelle.dto.CreateCredentialRequest;
import com.narturebelle.dto.CreateProviderRequest;
import com.narturebelle.dto.CreateServiceOfferingRequest;
import com.narturebelle.dto.CreateSlotsRequest;
import com.narturebelle.dto.ProviderAdminResponse;
import com.narturebelle.dto.SlotResponse;
import com.narturebelle.dto.UpdateProviderStatusRequest;
import com.narturebelle.dto.VerifyProviderRequest;
import com.narturebelle.model.Provider;
import com.narturebelle.model.ProviderCredential;
import com.narturebelle.model.ServiceOffering;
import com.narturebelle.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** /api/v1/admin/** is already role-gated by SecurityConfig (any *_ADMIN role) — no extra check needed here. */
@RestController
@RequestMapping("/api/v1/admin/providers")
public class AdminProviderController {

    private final ProviderService providerService;

    public AdminProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @PostMapping
    public ResponseEntity<ProviderAdminResponse> create(@Valid @RequestBody CreateProviderRequest req) {
        Provider provider = providerService.createProvider(req);
        return ResponseEntity.ok(toAdminResponse(provider));
    }

    @GetMapping
    public ResponseEntity<List<ProviderAdminResponse>> list() {
        List<ProviderAdminResponse> providers = providerService.listAll().stream()
                .map(this::toAdminResponse)
                .toList();
        return ResponseEntity.ok(providers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProviderAdminResponse> getOne(@PathVariable Long id) {
        Provider provider = providerService.find(id).orElse(null);
        if (provider == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toAdminResponse(provider));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<ProviderAdminResponse> verify(@PathVariable Long id, @Valid @RequestBody VerifyProviderRequest req) {
        Provider provider = providerService.verify(id, req.getReason());
        return ResponseEntity.ok(toAdminResponse(provider));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProviderAdminResponse> updateStatus(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateProviderStatusRequest req) {
        Provider provider = providerService.updateStatus(id, req.getStatus(), req.getReason());
        return ResponseEntity.ok(toAdminResponse(provider));
    }

    @PostMapping("/{id}/credentials")
    public ResponseEntity<ProviderCredential> addCredential(@PathVariable Long id, @Valid @RequestBody CreateCredentialRequest req) {
        ProviderCredential credential = providerService.addCredential(id, req);
        return ResponseEntity.ok(credential);
    }

    @PostMapping("/{id}/services")
    public ResponseEntity<ServiceOffering> addService(@PathVariable Long id, @Valid @RequestBody CreateServiceOfferingRequest req) {
        ServiceOffering offering = providerService.addServiceOffering(id, req);
        return ResponseEntity.ok(offering);
    }

    @PostMapping("/{id}/slots")
    public ResponseEntity<List<SlotResponse>> addSlots(@PathVariable Long id, @Valid @RequestBody CreateSlotsRequest req) {
        List<SlotResponse> slots = providerService.addSlots(id, req).stream().map(SlotResponse::from).toList();
        return ResponseEntity.ok(slots);
    }

    private ProviderAdminResponse toAdminResponse(Provider provider) {
        return ProviderAdminResponse.from(provider, providerService.credentialsFor(provider.getId()));
    }
}
