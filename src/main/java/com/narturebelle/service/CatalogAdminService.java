package com.narturebelle.service;

import com.narturebelle.dto.ChecklistTaskRequest;
import com.narturebelle.dto.HospitalPartnerRequest;
import com.narturebelle.dto.ProductRequest;
import com.narturebelle.exception.CatalogItemInUseException;
import com.narturebelle.exception.ChecklistTaskNotFoundException;
import com.narturebelle.exception.HospitalNotFoundException;
import com.narturebelle.exception.ProductNotFoundException;
import com.narturebelle.model.ChecklistTask;
import com.narturebelle.model.HospitalPartner;
import com.narturebelle.model.Product;
import com.narturebelle.repository.ChecklistTaskRepository;
import com.narturebelle.repository.HospitalPartnerRepository;
import com.narturebelle.repository.HospitalReferralRepository;
import com.narturebelle.repository.ProductRecommendationDismissalRepository;
import com.narturebelle.repository.ProductRepository;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

/**
 * Admin CRUD for the public catalog -- hospitals, products, checklist tasks -- so production can hold
 * real entries instead of DataInitializer's sample content (which is no longer seeded under prod).
 * Updates are full replacements. Deletes refuse with 409 while user data still points at the row.
 */
@Service
public class CatalogAdminService {

    private final HospitalPartnerRepository hospitalPartnerRepository;
    private final ProductRepository productRepository;
    private final ChecklistTaskRepository checklistTaskRepository;
    private final HospitalReferralRepository hospitalReferralRepository;
    private final ProductRecommendationDismissalRepository dismissalRepository;
    private final AuditService auditService;

    public CatalogAdminService(HospitalPartnerRepository hospitalPartnerRepository,
                               ProductRepository productRepository,
                               ChecklistTaskRepository checklistTaskRepository,
                               HospitalReferralRepository hospitalReferralRepository,
                               ProductRecommendationDismissalRepository dismissalRepository,
                               AuditService auditService) {
        this.hospitalPartnerRepository = hospitalPartnerRepository;
        this.productRepository = productRepository;
        this.checklistTaskRepository = checklistTaskRepository;
        this.hospitalReferralRepository = hospitalReferralRepository;
        this.dismissalRepository = dismissalRepository;
        this.auditService = auditService;
    }

    // ---- Hospitals ----

    public HospitalPartner createHospital(HospitalPartnerRequest req) {
        HospitalPartner saved = hospitalPartnerRepository.save(apply(new HospitalPartner(), req));
        auditService.record("CATALOG_HOSPITAL_CREATED", "HOSPITAL_PARTNER", String.valueOf(saved.getId()), "Admin added a hospital");
        return saved;
    }

    public HospitalPartner updateHospital(Long id, HospitalPartnerRequest req) {
        HospitalPartner hospital = hospitalPartnerRepository.findById(id)
                .orElseThrow(() -> new HospitalNotFoundException("Hospital " + id + " not found."));
        HospitalPartner saved = hospitalPartnerRepository.save(apply(hospital, req));
        auditService.record("CATALOG_HOSPITAL_UPDATED", "HOSPITAL_PARTNER", String.valueOf(id), "Admin updated a hospital");
        return saved;
    }

    @Transactional
    public void deleteHospital(Long id) {
        if (!hospitalPartnerRepository.existsById(id)) {
            throw new HospitalNotFoundException("Hospital " + id + " not found.");
        }
        if (hospitalReferralRepository.existsByHospitalId(id)) {
            throw new CatalogItemInUseException("Hospital " + id + " has referrals and can't be deleted.");
        }
        hospitalPartnerRepository.deleteById(id);
        auditService.record("CATALOG_HOSPITAL_DELETED", "HOSPITAL_PARTNER", String.valueOf(id), "Admin deleted a hospital");
    }

    private HospitalPartner apply(HospitalPartner hospital, HospitalPartnerRequest req) {
        hospital.setName(req.getName());
        hospital.setCity(req.getCity());
        hospital.setPincode(req.getPincode());
        hospital.setAddress(req.getAddress());
        hospital.setSpecialty(req.getSpecialty());
        hospital.setRating(req.getRating());
        hospital.setReviewCount(req.getReviewCount());
        hospital.setPhone(req.getPhone());
        hospital.setConsultationUrl(req.getConsultationUrl());
        hospital.setDiscountDetails(req.getDiscountDetails());
        hospital.setPartnerTier(req.getPartnerTier());
        hospital.setImageUrl(req.getImageUrl());
        return hospital;
    }

    // ---- Products ----

    public Product createProduct(ProductRequest req) {
        Product saved = productRepository.save(apply(new Product(), req));
        auditService.record("CATALOG_PRODUCT_CREATED", "PRODUCT", String.valueOf(saved.getId()), "Admin added a product");
        return saved;
    }

    public Product updateProduct(Long id, ProductRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product " + id + " not found."));
        Product saved = productRepository.save(apply(product, req));
        auditService.record("CATALOG_PRODUCT_UPDATED", "PRODUCT", String.valueOf(id), "Admin updated a product");
        return saved;
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException("Product " + id + " not found.");
        }
        if (dismissalRepository.existsByProductId(id)) {
            throw new CatalogItemInUseException("Product " + id + " has been dismissed by users and can't be deleted.");
        }
        productRepository.deleteById(id);
        auditService.record("CATALOG_PRODUCT_DELETED", "PRODUCT", String.valueOf(id), "Admin deleted a product");
    }

    private Product apply(Product product, ProductRequest req) {
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setPrice(req.getPrice());
        product.setOriginalPrice(req.getOriginalPrice());
        product.setStage(req.getStage());
        product.setTier(req.getTier());
        product.setClinicalRationale(req.getClinicalRationale());
        product.setImageUrl(req.getImageUrl());
        product.setRating(req.getRating());
        product.setReviewCount(req.getReviewCount());
        product.setDoctorApproved(req.getDoctorApproved());
        product.setCategory(req.getCategory());
        product.setKeyBenefits(new ArrayList<>(req.getKeyBenefits() != null ? req.getKeyBenefits() : java.util.List.of()));
        product.setTargetSymptoms(new ArrayList<>(req.getTargetSymptoms() != null ? req.getTargetSymptoms() : java.util.List.of()));
        return product;
    }

    // ---- Checklist tasks ----

    public ChecklistTask createChecklistTask(ChecklistTaskRequest req) {
        ChecklistTask saved = checklistTaskRepository.save(apply(new ChecklistTask(), req));
        auditService.record("CATALOG_CHECKLIST_TASK_CREATED", "CHECKLIST_TASK", String.valueOf(saved.getId()), "Admin added a checklist task");
        return saved;
    }

    public ChecklistTask updateChecklistTask(Long id, ChecklistTaskRequest req) {
        ChecklistTask task = checklistTaskRepository.findById(id)
                .orElseThrow(() -> new ChecklistTaskNotFoundException("Checklist task " + id + " not found."));
        ChecklistTask saved = checklistTaskRepository.save(apply(task, req));
        auditService.record("CATALOG_CHECKLIST_TASK_UPDATED", "CHECKLIST_TASK", String.valueOf(id), "Admin updated a checklist task");
        return saved;
    }

    public void deleteChecklistTask(Long id) {
        if (!checklistTaskRepository.existsById(id)) {
            throw new ChecklistTaskNotFoundException("Checklist task " + id + " not found.");
        }
        checklistTaskRepository.deleteById(id);
        auditService.record("CATALOG_CHECKLIST_TASK_DELETED", "CHECKLIST_TASK", String.valueOf(id), "Admin deleted a checklist task");
    }

    private ChecklistTask apply(ChecklistTask task, ChecklistTaskRequest req) {
        task.setStage(req.getStage());
        task.setTestName(req.getTestName());
        task.setWeeksDue(req.getWeeksDue());
        task.setDescription(req.getDescription());
        task.setIsMandatoryMedicalTest(req.getIsMandatoryMedicalTest());
        task.setCategory(req.getCategory());
        task.setRecommendedFacilityType(req.getRecommendedFacilityType());
        task.setSponsoredPartnerName(req.getSponsoredPartnerName());
        task.setSponsoredPartnerBadge(req.getSponsoredPartnerBadge());
        task.setPartnerDiscountCode(req.getPartnerDiscountCode());
        task.setPartnerBookingUrl(req.getPartnerBookingUrl());
        return task;
    }
}
