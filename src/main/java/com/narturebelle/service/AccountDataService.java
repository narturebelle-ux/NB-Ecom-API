package com.narturebelle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.exception.DeletionRequestConflictException;
import com.narturebelle.exception.DeletionRequestNotFoundException;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.AccountDeletionRequest;
import com.narturebelle.model.Child;
import com.narturebelle.model.DeletionRequestStatus;
import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.Journey;
import com.narturebelle.model.Order;
import com.narturebelle.model.RecordStatus;
import com.narturebelle.model.User;
import com.narturebelle.repository.*;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Self-service data export and account-deletion requests (Phase 6). */
@Service
public class AccountDataService {

    private static final Set<DeletionRequestStatus> OPEN = EnumSet.of(DeletionRequestStatus.REQUESTED, DeletionRequestStatus.IN_PROGRESS);

    private final UserRepository userRepository;
    private final ConsentRepository consentRepository;
    private final JourneyRepository journeyRepository;
    private final JourneyStageEventRepository journeyStageEventRepository;
    private final OnboardingIntakeRepository onboardingIntakeRepository;
    private final OnboardingCallRepository onboardingCallRepository;
    private final OnboardingCallbackRequestRepository onboardingCallbackRequestRepository;
    private final OnboardingDocumentSignatureRepository onboardingDocumentSignatureRepository;
    private final OnboardingCompletionRepository onboardingCompletionRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EntitlementRepository entitlementRepository;
    private final BookingRepository bookingRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final RecordShareRepository recordShareRepository;
    private final ReminderRepository reminderRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final ChildRepository childRepository;
    private final ChildVaccinationRepository childVaccinationRepository;
    private final HospitalReferralRepository hospitalReferralRepository;
    private final CommunityEventRsvpRepository communityEventRsvpRepository;
    private final ProductRecommendationDismissalRepository productRecommendationDismissalRepository;
    private final AccountDeletionRequestRepository accountDeletionRequestRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AccountDataService(UserRepository userRepository,
                              ConsentRepository consentRepository,
                              JourneyRepository journeyRepository,
                              JourneyStageEventRepository journeyStageEventRepository,
                              OnboardingIntakeRepository onboardingIntakeRepository,
                              OnboardingCallRepository onboardingCallRepository,
                              OnboardingCallbackRequestRepository onboardingCallbackRequestRepository,
                              OnboardingDocumentSignatureRepository onboardingDocumentSignatureRepository,
                              OnboardingCompletionRepository onboardingCompletionRepository,
                              OrderRepository orderRepository,
                              PaymentRepository paymentRepository,
                              EntitlementRepository entitlementRepository,
                              BookingRepository bookingRepository,
                              HealthRecordRepository healthRecordRepository,
                              RecordShareRepository recordShareRepository,
                              ReminderRepository reminderRepository,
                              NotificationPreferenceRepository notificationPreferenceRepository,
                              ChildRepository childRepository,
                              ChildVaccinationRepository childVaccinationRepository,
                              HospitalReferralRepository hospitalReferralRepository,
                              CommunityEventRsvpRepository communityEventRsvpRepository,
                              ProductRecommendationDismissalRepository productRecommendationDismissalRepository,
                              AccountDeletionRequestRepository accountDeletionRequestRepository,
                              AuditService auditService,
                              ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.consentRepository = consentRepository;
        this.journeyRepository = journeyRepository;
        this.journeyStageEventRepository = journeyStageEventRepository;
        this.onboardingIntakeRepository = onboardingIntakeRepository;
        this.onboardingCallRepository = onboardingCallRepository;
        this.onboardingCallbackRequestRepository = onboardingCallbackRequestRepository;
        this.onboardingDocumentSignatureRepository = onboardingDocumentSignatureRepository;
        this.onboardingCompletionRepository = onboardingCompletionRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.entitlementRepository = entitlementRepository;
        this.bookingRepository = bookingRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.recordShareRepository = recordShareRepository;
        this.reminderRepository = reminderRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.childRepository = childRepository;
        this.childVaccinationRepository = childVaccinationRepository;
        this.hospitalReferralRepository = hospitalReferralRepository;
        this.communityEventRsvpRepository = communityEventRsvpRepository;
        this.productRecommendationDismissalRepository = productRecommendationDismissalRepository;
        this.accountDeletionRequestRepository = accountDeletionRequestRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Everything the server holds about the caller, as pretty-printed JSON. Serialised inside the
     * transaction so element collections load. Health-record files themselves aren't included --
     * only their metadata; the files stay downloadable through /records/{id}/download-url.
     */
    @Transactional(readOnly = true)
    public byte[] export(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException("User profile not found."));

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("firstName", user.getFirstName());
        profile.put("lastName", user.getLastName());
        profile.put("email", user.getEmail());
        profile.put("emailVerified", user.getEmailVerified());
        profile.put("phone", user.getPhone());
        profile.put("phoneVerified", user.getPhoneVerified());
        profile.put("authProvider", user.getAuthProvider());
        profile.put("roles", user.getRoles());
        profile.put("createdAt", user.getCreatedAt());

        List<Journey> journeys = journeyRepository.findByUserIdOrderByStartedAtDesc(userId);
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<HealthRecord> records = healthRecordRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(userId, RecordStatus.DELETED);
        List<Child> children = childRepository.findByUserIdOrderByDateOfBirthAsc(userId);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", LocalDateTime.now());
        export.put("profile", profile);
        export.put("consents", consentRepository.findByUserIdOrderByGrantedAtDesc(userId));
        export.put("journeys", journeys);
        export.put("journeyStageEvents", journeys.stream()
                .flatMap(j -> journeyStageEventRepository.findByJourneyIdOrderByOccurredAtAsc(j.getId()).stream()).toList());
        export.put("onboardingIntake", onboardingIntakeRepository.findByUserId(userId).orElse(null));
        export.put("onboardingCall", onboardingCallRepository.findByUserId(userId).orElse(null));
        export.put("onboardingCallbackRequests", onboardingCallbackRequestRepository.findByUserIdOrderByCreatedAtDesc(userId));
        export.put("onboardingDocumentSignatures", onboardingDocumentSignatureRepository.findByUserId(userId));
        export.put("onboardingCompletion", onboardingCompletionRepository.findByUserId(userId).orElse(null));
        export.put("orders", orders);
        export.put("payments", orders.stream()
                .flatMap(o -> paymentRepository.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream()).toList());
        export.put("entitlements", entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId));
        export.put("bookings", bookingRepository.findByUserIdOrderByCreatedAtDesc(userId));
        export.put("healthRecords", records);
        export.put("recordShares", records.stream()
                .flatMap(r -> recordShareRepository.findByRecordIdOrderByCreatedAtDesc(r.getId()).stream()).toList());
        export.put("reminders", reminderRepository.findByUserIdOrderByCreatedAtDesc(userId));
        export.put("notificationPreferences", notificationPreferenceRepository.findByUserId(userId));
        export.put("children", children);
        export.put("childVaccinations", children.stream()
                .flatMap(c -> childVaccinationRepository.findByChildId(c.getId()).stream()).toList());
        export.put("hospitalReferrals", hospitalReferralRepository.findByUserIdOrderByCreatedAtDesc(userId));
        export.put("joyCircleRsvps", communityEventRsvpRepository.findByUserId(userId));
        export.put("productRecommendationDismissals", productRecommendationDismissalRepository.findByUserId(userId));
        export.put("deletionRequests", accountDeletionRequestRepository.findByUserIdOrderByRequestedAtDesc(userId));

        auditService.record("ACCOUNT_DATA_EXPORTED", "USER", String.valueOf(userId), "User downloaded a copy of their data");
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise the data export.", e);
        }
    }

    /** The most recent request that wasn't cancelled. */
    public AccountDeletionRequest currentDeletionRequest(Long userId) {
        return accountDeletionRequestRepository.findTopByUserIdAndStatusNotOrderByRequestedAtDesc(userId, DeletionRequestStatus.CANCELLED)
                .orElseThrow(() -> new DeletionRequestNotFoundException("You have no account deletion request."));
    }

    @Transactional
    public AccountDeletionRequest requestDeletion(Long userId) {
        if (accountDeletionRequestRepository.existsByUserIdAndStatusIn(userId, OPEN)) {
            throw new DeletionRequestConflictException("DELETION_ALREADY_REQUESTED",
                    "You already have an account deletion request in progress.");
        }
        AccountDeletionRequest request = new AccountDeletionRequest();
        request.setUserId(userId);
        request.setStatus(DeletionRequestStatus.REQUESTED);
        AccountDeletionRequest saved = accountDeletionRequestRepository.save(request);
        auditService.record("ACCOUNT_DELETION_REQUESTED", "ACCOUNT_DELETION_REQUEST", String.valueOf(saved.getId()),
                "User requested account deletion");
        return saved;
    }

    /** Only a REQUESTED request can be withdrawn -- once an admin has started, it's theirs to finish or reject. */
    @Transactional
    public void cancelDeletion(Long userId) {
        AccountDeletionRequest request = currentDeletionRequest(userId);
        if (request.getStatus() != DeletionRequestStatus.REQUESTED) {
            throw new DeletionRequestConflictException("DELETION_NOT_CANCELLABLE",
                    "This deletion request is already " + request.getStatus() + " and can't be cancelled.");
        }
        request.setStatus(DeletionRequestStatus.CANCELLED);
        accountDeletionRequestRepository.save(request);
        auditService.record("ACCOUNT_DELETION_CANCELLED", "ACCOUNT_DELETION_REQUEST", String.valueOf(request.getId()),
                "User cancelled their account deletion request");
    }

    public List<AccountDeletionRequest> listDeletionRequests() {
        return accountDeletionRequestRepository.findAllByOrderByRequestedAtDesc();
    }

    /** REQUESTED -> IN_PROGRESS | REJECTED; IN_PROGRESS -> COMPLETED | REJECTED. Marking COMPLETED records that an
     * admin finished the manual erasure -- it doesn't erase anything itself. */
    @Transactional
    public AccountDeletionRequest updateDeletionStatus(Long requestId, DeletionRequestStatus next) {
        AccountDeletionRequest request = accountDeletionRequestRepository.findById(requestId)
                .orElseThrow(() -> new DeletionRequestNotFoundException("Deletion request " + requestId + " not found."));
        DeletionRequestStatus current = request.getStatus();
        boolean allowed = (current == DeletionRequestStatus.REQUESTED
                        && (next == DeletionRequestStatus.IN_PROGRESS || next == DeletionRequestStatus.REJECTED))
                || (current == DeletionRequestStatus.IN_PROGRESS
                        && (next == DeletionRequestStatus.COMPLETED || next == DeletionRequestStatus.REJECTED));
        if (!allowed) {
            throw new DeletionRequestConflictException("INVALID_DELETION_STATUS_TRANSITION",
                    "Can't move a deletion request from " + current + " to " + next + ".");
        }
        request.setStatus(next);
        AccountDeletionRequest saved = accountDeletionRequestRepository.save(request);
        auditService.record("ACCOUNT_DELETION_STATUS_CHANGED", "ACCOUNT_DELETION_REQUEST", String.valueOf(saved.getId()),
                "Admin moved an account deletion request", Map.of("from", current, "to", next));
        return saved;
    }
}
