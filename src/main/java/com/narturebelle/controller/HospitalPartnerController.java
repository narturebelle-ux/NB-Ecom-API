package com.narturebelle.controller;

import com.narturebelle.model.HospitalPartner;
import com.narturebelle.repository.HospitalPartnerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hospitals")
public class HospitalPartnerController {

    private final HospitalPartnerRepository hospitalPartnerRepository;

    public HospitalPartnerController(HospitalPartnerRepository hospitalPartnerRepository) {
        this.hospitalPartnerRepository = hospitalPartnerRepository;
    }

    @GetMapping
    public ResponseEntity<List<HospitalPartner>> getHospitalPartners(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String pincode) {
        if (pincode != null && !pincode.isBlank()) {
            List<HospitalPartner> byPincode = hospitalPartnerRepository.findByPincode(pincode);
            if (!byPincode.isEmpty()) {
                return ResponseEntity.ok(byPincode);
            }
        }
        if (city != null && !city.isBlank()) {
            return ResponseEntity.ok(hospitalPartnerRepository.findByCityIgnoreCase(city));
        }
        return ResponseEntity.ok(hospitalPartnerRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HospitalPartner> getHospitalById(@PathVariable Long id) {
        return hospitalPartnerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
