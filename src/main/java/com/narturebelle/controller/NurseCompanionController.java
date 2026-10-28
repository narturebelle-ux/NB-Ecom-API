package com.narturebelle.controller;

import com.narturebelle.dto.NurseBookingRequest;
import com.narturebelle.model.NurseBooking;
import com.narturebelle.model.NursePackage;
import com.narturebelle.repository.NurseBookingRepository;
import com.narturebelle.repository.NursePackageRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/nurse-companion")
public class NurseCompanionController {

    private final NursePackageRepository nursePackageRepository;
    private final NurseBookingRepository nurseBookingRepository;

    public NurseCompanionController(NursePackageRepository nursePackageRepository,
                                    NurseBookingRepository nurseBookingRepository) {
        this.nursePackageRepository = nursePackageRepository;
        this.nurseBookingRepository = nurseBookingRepository;
    }

    @GetMapping("/packages")
    public ResponseEntity<List<NursePackage>> getPackages() {
        return ResponseEntity.ok(nursePackageRepository.findAll());
    }

    @PostMapping("/book")
    public ResponseEntity<Map<String, Object>> bookNurseCompanion(@Valid @RequestBody NurseBookingRequest request) {
        NurseBooking booking = new NurseBooking();
        booking.setClientName(request.getClientName());
        booking.setClientPhone(request.getClientPhone());
        booking.setClientEmail(request.getClientEmail());
        booking.setCurrentStage(request.getCurrentStage());
        booking.setPreferredLanguage(request.getPreferredLanguage());
        booking.setPackageId(request.getPackageId());
        booking.setCity(request.getCity());
        booking.setNotes(request.getNotes());

        if (request.getPackageId() != null) {
            nursePackageRepository.findById(request.getPackageId())
                    .ifPresent(pkg -> booking.setPackageName(pkg.getName()));
        }

        NurseBooking saved = nurseBookingRepository.save(booking);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("bookingId", saved.getId());
        response.put("status", saved.getStatus());
        response.put("message", "Congratulations! Your dedicated maternal nurse companion request has been received. Our senior care coordinator will call you within 2 business hours.");
        response.put("booking", saved);

        return ResponseEntity.ok(response);
    }
}
