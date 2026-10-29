package com.narturebelle.controller;

import com.narturebelle.dto.ProviderResponse;
import com.narturebelle.dto.SlotResponse;
import com.narturebelle.model.Modality;
import com.narturebelle.model.Provider;
import com.narturebelle.model.Role;
import com.narturebelle.service.ProviderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public ResponseEntity<List<ProviderResponse>> list(@RequestParam(required = false) Role professionalType,
                                                         @RequestParam(required = false) String language,
                                                         @RequestParam(required = false) String city,
                                                         @RequestParam(required = false) Modality modality) {
        List<ProviderResponse> providers = providerService.listPublic(professionalType, language, city, modality)
                .stream().map(ProviderResponse::from).toList();
        return ResponseEntity.ok(providers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProviderResponse> getOne(@PathVariable Long id) {
        Provider provider = providerService.find(id).orElse(null);
        if (provider == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ProviderResponse.from(provider));
    }

    @GetMapping("/{id}/slots")
    public ResponseEntity<List<SlotResponse>> slots(@PathVariable Long id) {
        if (providerService.find(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<SlotResponse> slots = providerService.openSlotsFor(id).stream().map(SlotResponse::from).toList();
        return ResponseEntity.ok(slots);
    }
}
