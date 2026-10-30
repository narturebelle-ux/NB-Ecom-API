package com.narturebelle.service;

import com.narturebelle.dto.BookOnboardingCallRequest;
import com.narturebelle.dto.CreateCallbackRequestRequest;
import com.narturebelle.dto.CreateOnboardingCallSlotsRequest;
import com.narturebelle.dto.SignOnboardingDocumentRequest;
import com.narturebelle.dto.UpsertOnboardingIntakeRequest;
import com.narturebelle.exception.OnboardingCallAlreadyBookedException;
import com.narturebelle.exception.OnboardingCallSlotNotAvailableException;
import com.narturebelle.exception.OnboardingDocumentNotFoundException;
import com.narturebelle.exception.OnboardingIncompleteException;
import com.narturebelle.model.OnboardingCall;
import com.narturebelle.model.OnboardingCallSlot;
import com.narturebelle.model.OnboardingCallbackRequest;
import com.narturebelle.model.OnboardingCompletion;
import com.narturebelle.model.OnboardingDocument;
import com.narturebelle.model.OnboardingDocumentSignature;
import com.narturebelle.model.OnboardingIntake;
import com.narturebelle.model.OrderStatus;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.model.User;
import com.narturebelle.repository.OnboardingCallRepository;
import com.narturebelle.repository.OnboardingCallSlotRepository;
import com.narturebelle.repository.OnboardingCallbackRequestRepository;
import com.narturebelle.repository.OnboardingCompletionRepository;
import com.narturebelle.repository.OnboardingDocumentRepository;
import com.narturebelle.repository.OnboardingDocumentSignatureRepository;
import com.narturebelle.repository.OnboardingIntakeRepository;
import com.narturebelle.repository.OrderRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OnboardingService {

    private final OnboardingIntakeRepository onboardingIntakeRepository;
    private final OnboardingCallSlotRepository onboardingCallSlotRepository;
    private final OnboardingCallRepository onboardingCallRepository;
    private final OnboardingCallbackRequestRepository onboardingCallbackRequestRepository;
    private final OnboardingDocumentRepository onboardingDocumentRepository;
    private final OnboardingDocumentSignatureRepository onboardingDocumentSignatureRepository;
    private final OnboardingCompletionRepository onboardingCompletionRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public OnboardingService(OnboardingIntakeRepository onboardingIntakeRepository,
                              OnboardingCallSlotRepository onboardingCallSlotRepository,
                              OnboardingCallRepository onboardingCallRepository,
                              OnboardingCallbackRequestRepository onboardingCallbackRequestRepository,
                              OnboardingDocumentRepository onboardingDocumentRepository,
                              OnboardingDocumentSignatureRepository onboardingDocumentSignatureRepository,
                              OnboardingCompletionRepository onboardingCompletionRepository,
                              OrderRepository orderRepository,
                              UserRepository userRepository,
                              NotificationService notificationService,
                              AuditService auditService) {
        this.onboardingIntakeRepository = onboardingIntakeRepository;
        this.onboardingCallSlotRepository = onboardingCallSlotRepository;
        this.onboardingCallRepository = onboardingCallRepository;
        this.onboardingCallbackRequestRepository = onboardingCallbackRequestRepository;
        this.onboardingDocumentRepository = onboardingDocumentRepository;
        this.onboardingDocumentSignatureRepository = onboardingDocumentSignatureRepository;
        this.onboardingCompletionRepository = onboardingCompletionRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
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

    public OnboardingCallbackRequest requestCallback(Long userId, CreateCallbackRequestRequest req) {
        OnboardingCallbackRequest request = new OnboardingCallbackRequest();
        request.setUserId(userId);
        request.setNote(req.getNote());
        OnboardingCallbackRequest saved = onboardingCallbackRequestRepository.save(request);
        auditService.record("ONBOARDING_CALLBACK_REQUESTED", "ONBOARDING_CALLBACK_REQUEST", String.valueOf(saved.getId()),
                "User asked to be called back for onboarding (no open slots)");
        return saved;
    }

    public List<OnboardingCallbackRequest> listCallbackRequests() {
        return onboardingCallbackRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<OnboardingCallbackRequest> findLatestCallbackRequest(Long userId) {
        return onboardingCallbackRequestRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
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

    public List<OnboardingDocument> listActiveDocuments() {
        return onboardingDocumentRepository.findByActiveTrue();
    }

    public Optional<OnboardingDocument> findDocument(Long id) {
        return onboardingDocumentRepository.findById(id);
    }

    public Optional<OnboardingDocumentSignature> findSignature(Long userId, Long documentId) {
        return onboardingDocumentSignatureRepository.findByUserIdAndDocumentId(userId, documentId);
    }

    public List<OnboardingDocumentSignature> listSignatures(Long userId) {
        return onboardingDocumentSignatureRepository.findByUserId(userId);
    }

    /** Idempotent: re-signing an already-signed document just returns the existing signature
     * instead of erroring or creating a second row (the DB unique constraint backstops this). */
    @Transactional
    public OnboardingDocumentSignature signDocument(Long userId, SignOnboardingDocumentRequest req) {
        Optional<OnboardingDocumentSignature> existing = onboardingDocumentSignatureRepository
                .findByUserIdAndDocumentId(userId, req.getDocumentId());
        if (existing.isPresent()) {
            return existing.get();
        }

        OnboardingDocument document = onboardingDocumentRepository.findById(req.getDocumentId())
                .orElseThrow(() -> new OnboardingDocumentNotFoundException(
                        "Onboarding document " + req.getDocumentId() + " not found."));

        OnboardingDocumentSignature signature = new OnboardingDocumentSignature();
        signature.setDocumentId(document.getId());
        signature.setUserId(userId);
        signature.setTypedName(req.getTypedName());
        OnboardingDocumentSignature saved = onboardingDocumentSignatureRepository.save(signature);

        auditService.record("ONBOARDING_DOCUMENT_SIGNED", "ONBOARDING_DOCUMENT_SIGNATURE", String.valueOf(saved.getId()),
                "User signed an onboarding document", java.util.Map.of("documentType", document.getType(), "version", document.getVersion()));

        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getEmail() != null) {
            notificationService.sendTransactionalEmail(userId, "Your signed onboarding document",
                    "Thanks for signing \"" + document.getTitle() + "\" -- your signed copy will be sent to " + user.getEmail() + ".");
            saved.setEmailedAt(LocalDateTime.now());
            saved = onboardingDocumentSignatureRepository.save(saved);
        }
        return saved;
    }

    public Optional<OnboardingCompletion> findCompletion(Long userId) {
        return onboardingCompletionRepository.findByUserId(userId);
    }

    public boolean allActiveDocumentsSigned(Long userId) {
        List<OnboardingDocument> activeDocuments = listActiveDocuments();
        return activeDocuments.stream().allMatch(doc -> findSignature(userId, doc.getId()).isPresent());
    }

    /** Idempotent: completing twice just returns the original completedAt. Checks everything the
     * product owner's decided flow requires before documents/onboarded (decision log #13): intake,
     * a scheduled call OR a callback request (the sanctioned fallback when no slots exist), a paid
     * order, and every currently active document signed. */
    @Transactional
    public OnboardingCompletion complete(Long userId) {
        Optional<OnboardingCompletion> existing = onboardingCompletionRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<String> missing = new ArrayList<>();
        if (onboardingIntakeRepository.findByUserId(userId).isEmpty()) {
            missing.add("intake");
        }
        boolean hasCall = onboardingCallRepository.findByUserId(userId).isPresent();
        boolean hasCallbackRequest = onboardingCallbackRequestRepository.findTopByUserIdOrderByCreatedAtDesc(userId).isPresent();
        if (!hasCall && !hasCallbackRequest) {
            missing.add("onboarding call");
        }
        if (!orderRepository.existsByUserIdAndStatus(userId, OrderStatus.PAID)) {
            missing.add("payment");
        }
        if (!allActiveDocumentsSigned(userId)) {
            missing.add("document signatures");
        }
        if (!missing.isEmpty()) {
            throw new OnboardingIncompleteException(missing);
        }

        OnboardingCompletion completion = new OnboardingCompletion();
        completion.setUserId(userId);
        OnboardingCompletion saved = onboardingCompletionRepository.save(completion);
        auditService.record("ONBOARDING_COMPLETED", "ONBOARDING_COMPLETION", String.valueOf(saved.getId()),
                "User completed onboarding");
        return saved;
    }
}
