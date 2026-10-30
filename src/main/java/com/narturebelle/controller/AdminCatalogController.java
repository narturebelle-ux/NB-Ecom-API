package com.narturebelle.controller;

import com.narturebelle.dto.ChecklistTaskRequest;
import com.narturebelle.dto.HospitalPartnerRequest;
import com.narturebelle.dto.ProductRequest;
import com.narturebelle.model.ChecklistTask;
import com.narturebelle.model.HospitalPartner;
import com.narturebelle.model.Product;
import com.narturebelle.service.CatalogAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin writes for the public catalog. Reads stay on the existing public endpoints
 * (/hospitals, /products, /checklists), which return these same entity shapes.
 * /api/v1/admin/** is already role-gated by SecurityConfig (any *_ADMIN role) -- no extra check needed here.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {

    private final CatalogAdminService catalogAdminService;

    public AdminCatalogController(CatalogAdminService catalogAdminService) {
        this.catalogAdminService = catalogAdminService;
    }

    @PostMapping("/hospitals")
    public ResponseEntity<HospitalPartner> createHospital(@Valid @RequestBody HospitalPartnerRequest req) {
        return ResponseEntity.ok(catalogAdminService.createHospital(req));
    }

    @PutMapping("/hospitals/{id}")
    public ResponseEntity<HospitalPartner> updateHospital(@PathVariable Long id, @Valid @RequestBody HospitalPartnerRequest req) {
        return ResponseEntity.ok(catalogAdminService.updateHospital(id, req));
    }

    @DeleteMapping("/hospitals/{id}")
    public ResponseEntity<Void> deleteHospital(@PathVariable Long id) {
        catalogAdminService.deleteHospital(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/products")
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest req) {
        return ResponseEntity.ok(catalogAdminService.createProduct(req));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return ResponseEntity.ok(catalogAdminService.updateProduct(id, req));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        catalogAdminService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/checklists")
    public ResponseEntity<ChecklistTask> createChecklistTask(@Valid @RequestBody ChecklistTaskRequest req) {
        return ResponseEntity.ok(catalogAdminService.createChecklistTask(req));
    }

    @PutMapping("/checklists/{id}")
    public ResponseEntity<ChecklistTask> updateChecklistTask(@PathVariable Long id, @Valid @RequestBody ChecklistTaskRequest req) {
        return ResponseEntity.ok(catalogAdminService.updateChecklistTask(id, req));
    }

    @DeleteMapping("/checklists/{id}")
    public ResponseEntity<Void> deleteChecklistTask(@PathVariable Long id) {
        catalogAdminService.deleteChecklistTask(id);
        return ResponseEntity.noContent().build();
    }
}
