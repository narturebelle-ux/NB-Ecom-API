package com.narturebelle.controller;

import com.narturebelle.dto.LeadAssessmentRequest;
import com.narturebelle.model.LeadProfile;
import com.narturebelle.model.Product;
import com.narturebelle.repository.LeadProfileRepository;
import com.narturebelle.repository.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {

    private final LeadProfileRepository leadProfileRepository;
    private final ProductRepository productRepository;

    public LeadController(LeadProfileRepository leadProfileRepository,
                          ProductRepository productRepository) {
        this.leadProfileRepository = leadProfileRepository;
        this.productRepository = productRepository;
    }

    @PostMapping("/assess")
    public ResponseEntity<Map<String, Object>> submitAssessment(@Valid @RequestBody LeadAssessmentRequest request) {
        LeadProfile lead = leadProfileRepository.findByPhone(request.getPhone())
                .orElse(new LeadProfile());

        lead.setName(request.getName());
        lead.setEmail(request.getEmail());
        lead.setPhone(request.getPhone());
        lead.setStage(request.getStage());
        lead.setDueDate(request.getDueDate());
        lead.setCity(request.getCity());
        lead.setPincode(request.getPincode());
        lead.setReportedSymptoms(request.getSymptoms());
        lead.setLeadSource(request.getLeadSource() != null ? request.getLeadSource() : "ASSESSMENT_TOOL");

        LeadProfile saved = leadProfileRepository.save(lead);

        // Fetch tailored recommendations based on stage
        List<Product> stageProducts = request.getStage() != null ?
                productRepository.findByStage(request.getStage()) :
                productRepository.findAll();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("leadId", saved.getId());
        response.put("discountVoucher", "NARTURE10");
        response.put("message", "Your personalized Doctor-Approved pregnancy routine has been generated!");
        response.put("recommendedProducts", stageProducts);
        response.put("profile", saved);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/unlock-checklist")
    public ResponseEntity<Map<String, Object>> unlockChecklist(@Valid @RequestBody LeadAssessmentRequest request) {
        LeadProfile lead = leadProfileRepository.findByPhone(request.getPhone())
                .orElse(new LeadProfile());

        lead.setName(request.getName());
        lead.setEmail(request.getEmail());
        lead.setPhone(request.getPhone());
        lead.setStage(request.getStage());
        lead.setCity(request.getCity());
        lead.setPincode(request.getPincode());
        lead.setDueDate(request.getDueDate());
        lead.setLeadSource("CHECKLIST_UNLOCK");

        LeadProfile saved = leadProfileRepository.save(lead);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("leadId", saved.getId());
        response.put("message", "Checklist unlocked! Tailored medical tests and accredited local clinics for " + request.getCity() + " are now active.");
        response.put("profile", saved);

        return ResponseEntity.ok(response);
    }
}
