package com.narturebelle.service;

import com.narturebelle.dto.CreateCoordinatorRequest;
import com.narturebelle.exception.CoordinatorNotFoundException;
import com.narturebelle.model.Coordinator;
import com.narturebelle.repository.CoordinatorRepository;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Admin management of onboarding coordinators ("Belle persons"). */
@Service
public class CoordinatorService {

    private final CoordinatorRepository coordinatorRepository;
    private final AuditService auditService;

    public CoordinatorService(CoordinatorRepository coordinatorRepository, AuditService auditService) {
        this.coordinatorRepository = coordinatorRepository;
        this.auditService = auditService;
    }

    public List<Coordinator> list() {
        return coordinatorRepository.findAllByOrderByDisplayNameAsc();
    }

    public Coordinator create(CreateCoordinatorRequest req) {
        Coordinator coordinator = new Coordinator();
        coordinator.setDisplayName(req.getDisplayName().trim());
        coordinator.setLanguages(new ArrayList<>(OnboardingService.normaliseLanguages(req.getLanguages())));
        coordinator.setPhotoUrl(req.getPhotoUrl());
        coordinator.setBio(req.getBio());
        coordinator.setActive(true);
        Coordinator saved = coordinatorRepository.save(coordinator);
        auditService.record("COORDINATOR_CREATED", "COORDINATOR", String.valueOf(saved.getId()), "Admin added an onboarding coordinator");
        return saved;
    }

    public Coordinator setActive(Long id, boolean active) {
        Coordinator coordinator = coordinatorRepository.findById(id)
                .orElseThrow(() -> new CoordinatorNotFoundException("Coordinator " + id + " not found."));
        coordinator.setActive(active);
        Coordinator saved = coordinatorRepository.save(coordinator);
        auditService.record(active ? "COORDINATOR_ACTIVATED" : "COORDINATOR_DEACTIVATED", "COORDINATOR", String.valueOf(id),
                "Admin changed an onboarding coordinator's status");
        return saved;
    }
}
