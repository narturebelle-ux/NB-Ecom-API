package com.narturebelle.service;

import com.narturebelle.dto.BookOnboardingCallRequest;
import com.narturebelle.dto.CreateOnboardingCallSlotsRequest;
import com.narturebelle.dto.UpsertOnboardingIntakeRequest;
import com.narturebelle.exception.OnboardingCallAlreadyBookedException;
import com.narturebelle.exception.OnboardingCallSlotNotAvailableException;
import com.narturebelle.model.OnboardingCall;
import com.narturebelle.model.OnboardingCallSlot;
import com.narturebelle.model.OnboardingIntake;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.repository.OnboardingCallRepository;
import com.narturebelle.repository.OnboardingCallSlotRepository;
import com.narturebelle.repository.OnboardingIntakeRepository;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OnboardingService {

    private final OnboardingIntakeRepository onboardingIntakeRepository;
    private final OnboardingCallSlotRepository onboardingCallSlotRepository;
    private final OnboardingCallRepository onboardingCallRepository;
    private final AuditService auditService;

    public OnboardingService(OnboardingIntakeRepository onboardingIntakeRepository,
                              OnboardingCallSlotRepository onboardingCallSlotRepository,
                              OnboardingCallRepository onboardingCallRepository,
                              AuditService auditService) {
        this.onboardingIntakeRepository = onboardingIntakeRepository;
        this.onboardingCallSlotRepository = onboardingCallSlotRepository;
        this.onboardingCallRepository = onboardingCallRepository;
        this.auditService = auditService;
    }

    public Optional<OnboardingIntake> findIntake(Long userId) {
        return onboardingIntakeRepository.findByUserId(userId);
    }

    public OnboardingIntake upsertIntake(Long userId, UpsertOnboardingIntakeRequest req) {
        OnboardingIntake intake = onboardingIntakeRepository.findByUserId(userId).orElseGet(() -> {
            OnboardingIntake fresh = new OnboardingIntake();
            fresh.setUserId(userId);
            return fresh;
        });
        intake.setFirstPregnancy(req.getFirstPregnancy());
        intake.setMultiples(req.getMultiples());
        intake.setRiskFactors(req.getRiskFactors() != null ? req.getRiskFactors() : java.util.Set.of());
        intake.setSupportWanted(req.getSupportWanted() != null ? req.getSupportWanted() : java.util.Set.of());
        intake.setCity(req.getCity());
        intake.setLanguage(req.getLanguage());
        intake.setPreferredModality(req.getPreferredModality());
        intake.setPhone(req.getPhone());
        intake.setNotes(req.getNotes());
        OnboardingIntake saved = onboardingIntakeRepository.save(intake);
        // Metadata intentionally carries no risk-factor/notes content -- only that an intake was saved.
        auditService.record("ONBOARDING_INTAKE_SAVED", "ONBOARDING_INTAKE", String.valueOf(saved.getId()),
                "User submitted onboarding intake");
        return saved;
    }

    public List<OnboardingCallSlot> listOpenCallSlots() {
        return onboardingCallSlotRepository.findByStatusAndStartAtAfterOrderByStartAtAsc(SlotStatus.OPEN, LocalDateTime.now());
    }

    public Optional<OnboardingCall> findCall(Long userId) {
        return onboardingCallRepository.findByUserId(userId);
    }

    @Transactional
    public OnboardingCall bookCall(Long userId, BookOnboardingCallRequest req) {
        if (onboardingCallRepository.findByUserId(userId).isPresent()) {
            throw new OnboardingCallAlreadyBookedException("You already have an onboarding call booked.");
        }
        OnboardingCallSlot slot = onboardingCallSlotRepository.findById(req.getSlotId()).orElse(null);
        if (slot == null || slot.getStatus() != SlotStatus.OPEN) {
            throw new OnboardingCallSlotNotAvailableException("That call slot isn't available.");
        }
        int claimed = onboardingCallSlotRepository.claimSlot(slot.getId());
        if (claimed == 0) {
            throw new OnboardingCallSlotNotAvailableException("That call slot isn't available.");
        }
        OnboardingCall call = new OnboardingCall();
        call.setUserId(userId);
        call.setSlotId(slot.getId());
        call.setScheduledStart(slot.getStartAt());
        call.setScheduledEnd(slot.getEndAt());
        OnboardingCall saved = onboardingCallRepository.save(call);
        auditService.record("ONBOARDING_CALL_BOOKED", "ONBOARDING_CALL", String.valueOf(saved.getId()),
                "User booked an onboarding call");
        return saved;
    }

    public List<OnboardingCallSlot> createCallSlots(CreateOnboardingCallSlotsRequest req) {
        return req.getSlots().stream().map(window -> {
            OnboardingCallSlot slot = new OnboardingCallSlot();
            slot.setStartAt(window.getStartAt());
            slot.setEndAt(window.getEndAt());
            slot.setStatus(SlotStatus.OPEN);
            return onboardingCallSlotRepository.save(slot);
        }).toList();
    }
}
