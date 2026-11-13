package com.narturebelle.providers.service;

import com.narturebelle.storage.SignedDownloadUrl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.providers.dto.ApplicationDtos.DocumentResponse;
import com.narturebelle.providers.dto.ApplicationDtos.RequestedChangeResponse;
import com.narturebelle.providers.dto.FormDtos.FormOption;
import com.narturebelle.providers.dto.FormDtos.FormQuestion;
import com.narturebelle.providers.dto.FormDtos.FormSection;
import com.narturebelle.providers.dto.FormDtos.QuestionType;
import com.narturebelle.providers.dto.ReviewDtos.BankForReview;
import com.narturebelle.providers.dto.ReviewDtos.MedicalDecisionRequest;
import com.narturebelle.providers.dto.ReviewDtos.OperationsDecisionRequest;
import com.narturebelle.providers.dto.ReviewDtos.QueueItem;
import com.narturebelle.providers.dto.ReviewDtos.RequestedChangeInput;
import com.narturebelle.providers.dto.ReviewDtos.ReviewAnswer;
import com.narturebelle.providers.dto.ReviewDtos.ReviewDetail;
import com.narturebelle.providers.dto.ReviewDtos.ReviewHistoryItem;
import com.narturebelle.providers.dto.ReviewDtos.ReviewSection;
import com.narturebelle.providers.exception.ProviderOnboardingException;
import com.narturebelle.providers.model.ApplicationReview;
import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.providers.model.BankVerificationStatus;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.providers.model.ProviderBankDetails;
import com.narturebelle.providers.model.RequestedChange;
import com.narturebelle.providers.model.ReviewDecision;
import com.narturebelle.providers.model.ReviewStage;
import com.narturebelle.model.Role;
import com.narturebelle.model.Provider;
import com.narturebelle.model.User;
import com.narturebelle.providers.repository.ApplicationReviewRepository;
import com.narturebelle.providers.repository.ProviderApplicationRepository;
import com.narturebelle.providers.repository.ProviderBankDetailsRepository;
import com.narturebelle.providers.repository.RequestedChangeRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.providers.security.FieldEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Staff decisions. Operations checks first, then a different person, the Medical Reviewer, gives the final
 * decision. No other admin role can act here (SecurityConfig), and each stage is limited to its own role.
 */
@Service
public class ApplicationReviewService {

    public static final String FLAG_NO_REGISTRATION = "NO_REGISTRATION";
    public static final String FLAG_DUPLICATE_REGISTRATION = "DUPLICATE_REGISTRATION";
    public static final String FLAG_LICENCE_EXPIRED = "LICENCE_EXPIRED";

    /** The flags that stop an application being approved; the rest only draw a reviewer's attention. */
    private static final Set<String> BLOCKING_FLAGS = Set.of(FLAG_NO_REGISTRATION, FLAG_DUPLICATE_REGISTRATION, FLAG_LICENCE_EXPIRED);

    private final ProviderApplicationRepository applicationRepository;
    private final ApplicationReviewRepository reviewRepository;
    private final RequestedChangeRepository requestedChangeRepository;
    private final ProviderBankDetailsRepository bankRepository;
    private final UserRepository userRepository;
    private final ApplicationAnswerStore answerStore;
    private final ApplicationDocumentService documentService;
    private final ApplicationFormCatalog catalog;
    private final ApplicationSetupService setupService;
    private final ProviderLifecycleService lifecycleService;
    private final ProfileChangeService profileChangeService;
    private final FieldEncryptor encryptor;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final ApplicationNotifier notifier;
    private final int reapplyAfterMonths;

    public ApplicationReviewService(ProviderApplicationRepository applicationRepository, ApplicationReviewRepository reviewRepository,
                                    RequestedChangeRepository requestedChangeRepository, ProviderBankDetailsRepository bankRepository,
                                    UserRepository userRepository, ApplicationAnswerStore answerStore,
                                    ApplicationDocumentService documentService, ApplicationFormCatalog catalog,
                                    ApplicationSetupService setupService, ProviderLifecycleService lifecycleService,
                                    ProfileChangeService profileChangeService, FieldEncryptor encryptor, ObjectMapper objectMapper,
                                    AuthorizationService authorizationService, AuditService auditService, ApplicationNotifier notifier,
                                    @Value("${onboarding.reapply-after-months}") int reapplyAfterMonths) {
        this.applicationRepository = applicationRepository;
        this.reviewRepository = reviewRepository;
        this.requestedChangeRepository = requestedChangeRepository;
        this.bankRepository = bankRepository;
        this.userRepository = userRepository;
        this.answerStore = answerStore;
        this.documentService = documentService;
        this.catalog = catalog;
        this.setupService = setupService;
        this.lifecycleService = lifecycleService;
        this.profileChangeService = profileChangeService;
        this.encryptor = encryptor;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.notifier = notifier;
        this.reapplyAfterMonths = reapplyAfterMonths;
    }

    // ---- queue and detail ----

    public List<QueueItem> queue(Collection<ApplicationStatus> statuses) {
        Collection<ApplicationStatus> wanted = statuses == null || statuses.isEmpty()
                ? List.of(ApplicationStatus.OPERATIONS_REVIEW, ApplicationStatus.MEDICAL_REVIEW, ApplicationStatus.VERIFIED)
                : statuses;
        return applicationRepository.findByStatusInOrderBySubmittedAtAsc(wanted).stream().map(application -> {
            Map<String, String> answers = answerStore.readAll(application);
            return new QueueItem(application.getId(), answers.get(ApplicationFormCatalog.KEY_LEGAL_NAME), application.getProfessionalType(),
                    application.getStatus(), application.getSubmittedAt(), flags(application, answers), application.isRecheckRequired());
        }).toList();
    }

    /** Includes PAN, so every open is audited. Bank details go to Operations only, who run the test deposit. */
    public ReviewDetail detail(Long id) {
        ProviderApplication application = find(id);
        auditService.record("PROVIDER_APPLICATION_VIEWED", "PROVIDER_APPLICATION", String.valueOf(id), null);
        Map<String, String> answers = answerStore.readAll(application);
        User user = userRepository.findById(application.getUserId()).orElse(null);

        Map<String, FormQuestion> byKey = catalog.questionsByKey(application.getProfessionalType());
        List<ReviewSection> sections = catalog.sections(application.getProfessionalType()).stream()
                .map(section -> new ReviewSection(section.title(), reviewAnswers(section, answers, byKey))).toList();
        List<DocumentResponse> documents = documentService.list(id).stream().map(documentService::toResponse).toList();
        List<RequestedChangeResponse> changes = requestedChangeRepository.findByApplicationId(id).stream()
                .map(change -> new RequestedChangeResponse(change.getQuestionKey(), change.getNote())).toList();
        List<ReviewHistoryItem> history = reviewRepository.findByApplicationIdOrderByCreatedAtAsc(id).stream()
                .map(review -> new ReviewHistoryItem(review.getStage(), review.getReviewerUserId(), review.getDecision(), review.getReason(),
                        checklist(review.getChecklistJson()), review.getCreatedAt())).toList();

        BankForReview bank = null;
        if (authorizationService.hasRole(Role.OPERATIONS_ADMIN)) {
            bank = bankRepository.findById(id).map(found -> new BankForReview(found.getAccountHolderName(), encryptor.decrypt(found.getAccountNumberEncrypted()),
                    found.getIfsc(), found.getAccountType(), found.isGstRegistered(), found.getGstin(), found.getVerificationStatus())).orElse(null);
        }
        return new ReviewDetail(id, application.getUserId(), user == null ? null : user.getEmail(), user == null ? null : user.getPhone(),
                application.getProfessionalType(), application.getStatus(), flags(application, answers), sections, documents, changes, history,
                application.getOperationsReviewerId(), application.getConfirmedExperienceYears(), stages(application), application.isCanPrescribe(),
                application.getRegistrationNumber(), application.getLicenceExpiresOn(), application.getSubmittedAt(), application.getVerifiedAt(),
                application.isRecheckRequired(), application.getStatus().isApproved() ? setupService.status(application) : null, bank,
                profileChangeService.forApplication(id));
    }

    public ApplicationDocumentService.DownloadedDocument document(Long documentId) {
        ApplicationDocumentService.DownloadedDocument found = documentService.download(null, documentId);
        auditService.record("PROVIDER_DOCUMENT_DOWNLOADED", "PROVIDER_APPLICATION", String.valueOf(found.document().getApplicationId()),
                "Staff downloaded a document");
        return found;
    }

    public SignedDownloadUrl documentDownloadUrl(Long documentId, String disposition) {
        ApplicationDocumentService.DownloadLink found = documentService.downloadUrl(null, documentId, disposition);
        auditService.record("PROVIDER_DOCUMENT_DOWNLOADED", "PROVIDER_APPLICATION", String.valueOf(found.document().getApplicationId()),
                "Staff opened a document");
        return found.link();
    }

    // ---- decisions ----

    @Transactional
    public void operationsDecision(Long id, OperationsDecisionRequest req) {
        authorizationService.requireRole(Role.OPERATIONS_ADMIN);
        ProviderApplication application = requireStatus(id, ApplicationStatus.OPERATIONS_REVIEW);
        Long reviewer = requireIndependentReviewer(application);
        switch (req.decision()) {
            case PASS -> {
                requireNoBlockingFlags(application);
                application.setOperationsReviewerId(reviewer);
                application.setOperationsReviewedAt(LocalDateTime.now());
                application.setStatus(ApplicationStatus.MEDICAL_REVIEW);
                applicationRepository.save(application);
            }
            case CHANGES -> sendBack(application, req.changes(), req.reason());
            case REJECT -> reject(application, req.reason(), req.fraud());
            default -> throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Operations can pass, ask for changes, or reject.");
        }
        recordReview(application, ReviewStage.OPERATIONS, reviewer, req.decision(), req.reason(), req.checklist());
    }

    @Transactional
    public void medicalDecision(Long id, MedicalDecisionRequest req) {
        authorizationService.requireRole(Role.MEDICAL_REVIEWER);
        ProviderApplication application = requireStatus(id, ApplicationStatus.MEDICAL_REVIEW);
        Long reviewer = requireIndependentReviewer(application);
        if (reviewer.equals(application.getOperationsReviewerId())) {
            throw new ProviderOnboardingException(409, "SAME_REVIEWER", "The final decision must come from a different person than the Operations check.");
        }
        switch (req.decision()) {
            case APPROVE -> approve(application, reviewer, req);
            case CHANGES -> sendBack(application, req.changes(), req.reason());
            case REJECT -> reject(application, req.reason(), req.fraud());
            default -> throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "The Medical Reviewer can approve, send back, or reject.");
        }
        recordReview(application, ReviewStage.MEDICAL, reviewer, req.decision(), req.reason(), req.checklist());
    }

    private void approve(ProviderApplication application, Long reviewer, MedicalDecisionRequest req) {
        requireNoBlockingFlags(application);
        if (req.confirmedExperienceYears() == null || req.confirmedExperienceYears() < 0) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please enter the confirmed years of experience.");
        }
        if (req.clearedStages() == null || req.clearedStages().isEmpty()) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please choose the care stages this person is cleared for.");
        }
        Map<String, String> answers = answerStore.readAll(application);
        Provider provider = lifecycleService.createHiddenProvider(application, answers.get(ApplicationFormCatalog.KEY_DISPLAY_NAME),
                answers.get(ApplicationFormCatalog.KEY_BIO), answers.get(ApplicationFormCatalog.KEY_CITY),
                ApplicationAnswerStore.splitMulti(answers.get(ApplicationFormCatalog.KEY_LANGUAGES)));

        application.setMedicalReviewerId(reviewer);
        application.setMedicalReviewedAt(LocalDateTime.now());
        application.setConfirmedExperienceYears(req.confirmedExperienceYears());
        application.setClearedStages(String.join(",", req.clearedStages()));
        application.setCanPrescribe(catalog.canPrescribe(application.getProfessionalType()));
        application.setLicenceExpiresOn(licenceExpiry(answers));
        application.setProviderId(provider.getId());
        application.setVerifiedAt(LocalDateTime.now());
        application.setStatus(ApplicationStatus.VERIFIED);
        applicationRepository.save(application);

        auditService.record("PROVIDER_APPLICATION_APPROVED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), req.reason(),
                Map.of("providerId", provider.getId()));
        notifier.notifyUser(application.getUserId(), "Your NartureBelle application is approved",
                "Congratulations, your application is approved. Please sign in to finish setup: your agreement, bank details, training, services and availability. Mothers will see you once you are live.");
    }

    private void sendBack(ProviderApplication application, List<RequestedChangeInput> changes, String reason) {
        if (changes == null || changes.isEmpty()) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please choose the fields that need changes.");
        }
        Map<String, FormQuestion> byKey = catalog.questionsByKey(application.getProfessionalType());
        requestedChangeRepository.deleteByApplicationId(application.getId());
        requestedChangeRepository.flush();
        for (RequestedChangeInput input : changes) {
            if (!byKey.containsKey(input.questionKey())) {
                throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Unknown field: " + input.questionKey());
            }
            RequestedChange change = new RequestedChange();
            change.setApplicationId(application.getId());
            change.setQuestionKey(input.questionKey());
            change.setNote(input.note());
            requestedChangeRepository.save(change);
        }
        application.setStatus(ApplicationStatus.CHANGES_REQUESTED);
        application.setChangesRequestedAt(LocalDateTime.now());
        application.setLastActivityAt(LocalDateTime.now());
        applicationRepository.save(application);
        notifier.notifyUser(application.getUserId(), "Your NartureBelle application needs a change",
                "Our reviewers need a change to your application. Please sign in to see what to update.");
    }

    private void reject(ProviderApplication application, String reason, boolean fraud) {
        if (reason == null || reason.isBlank()) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please give a reason.");
        }
        application.setStatus(ApplicationStatus.REJECTED);
        application.setRejectionReason(reason);
        application.setFraudBlocked(fraud);
        application.setReapplyAfter(fraud ? null : LocalDate.now().plusMonths(reapplyAfterMonths));
        application.setClosedAt(LocalDateTime.now());
        applicationRepository.save(application);
        auditService.record("PROVIDER_APPLICATION_REJECTED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), reason, Map.of("fraud", fraud));
        notifier.notifyUser(application.getUserId(), "Your NartureBelle application",
                "We're unable to approve your application. Reason: " + reason
                        + (fraud ? "" : "\n\nYou can apply again from " + application.getReapplyAfter() + "."));
    }

    // ---- after approval ----

    /** No test-deposit provider is connected yet, so Operations runs it by hand and records the outcome here. */
    @Transactional
    public void verifyBank(Long id, boolean passed, String reason) {
        authorizationService.requireRole(Role.OPERATIONS_ADMIN);
        requireIndependentReviewer(find(id));
        ProviderBankDetails bank = bankRepository.findById(id)
                .orElseThrow(() -> new ProviderOnboardingException(404, "BANK_NOT_FOUND", "No bank details on this application."));
        bank.setVerificationStatus(passed ? BankVerificationStatus.PASSED : BankVerificationStatus.FAILED);
        bank.setVerifiedAt(LocalDateTime.now());
        bank.setVerifiedByUserId(authorizationService.requireUserId());
        bankRepository.save(bank);
        auditService.record(passed ? "PROVIDER_BANK_VERIFIED" : "PROVIDER_BANK_FAILED", "PROVIDER_APPLICATION", String.valueOf(id), reason);
        if (!passed) {
            notifier.notifyUser(find(id).getUserId(), "Please check your NartureBelle bank details",
                    "We couldn't verify your bank details with a test deposit. Please sign in and enter them again.");
        }
    }

    @Transactional
    public void completeRecheck(Long id, String reason) {
        authorizationService.requireRole(Role.OPERATIONS_ADMIN);
        ProviderApplication application = requireStatus(id, ApplicationStatus.VERIFIED);
        requireIndependentReviewer(application);
        application.setRecheckRequired(false);
        application.setVerifiedAt(LocalDateTime.now());
        applicationRepository.save(application);
        auditService.record("PROVIDER_APPROVAL_RECHECKED", "PROVIDER_APPLICATION", String.valueOf(id), reason);
    }

    @Transactional
    public void goLive(Long id) {
        authorizationService.requireRole(Role.OPERATIONS_ADMIN);
        ProviderApplication application = requireStatus(id, ApplicationStatus.VERIFIED);
        requireIndependentReviewer(application);
        var setup = setupService.status(application);
        if (!setup.readyForGoLive()) {
            throw new ProviderOnboardingException(409, "SETUP_INCOMPLETE", "This professional hasn't finished setup.", Map.of("missing", setup.missing()));
        }
        lifecycleService.goLive(application);
    }

    public void changeStatus(Long id, ApplicationStatus status, String reason) {
        authorizationService.requireRole(Role.OPERATIONS_ADMIN);
        ProviderApplication application = find(id);
        requireIndependentReviewer(application);
        lifecycleService.changeStatus(application, status, reason);
    }

    // ---- flags ----

    /** What reviewers should look at. Computed fresh so a later duplicate shows up on both applications. */
    public List<String> flags(ProviderApplication application, Map<String, String> answers) {
        List<String> flags = new ArrayList<>();
        if (!"YES".equals(answers.get(ApplicationFormCatalog.KEY_HOLDS_REGISTRATION))) flags.add(FLAG_NO_REGISTRATION);
        if (application.getRegistrationNumber() != null
                && applicationRepository.registrationHeldElsewhere(application.getRegistrationNumber(), application.getId())) {
            flags.add(FLAG_DUPLICATE_REGISTRATION);
        }
        LocalDate expiry = licenceExpiry(answers);
        if (expiry != null && expiry.isBefore(LocalDate.now())) flags.add(FLAG_LICENCE_EXPIRED);
        if ("YES".equals(answers.get("everSuspended"))) flags.add("PAST_SUSPENSION");
        if ("YES".equals(answers.get("criminalCase"))) flags.add("CRIMINAL_CASE");
        if ("NO".equals(answers.get("currentlyPractising"))) flags.add("NOT_PRACTISING");
        if ("NO".equals(answers.get("specialistRecorded"))) flags.add("SPECIALIST_NOT_RECORDED");
        if ("YES".equals(answers.get("qualifiedAbroad"))) flags.add("QUALIFIED_ABROAD");
        if ("YES".equals(answers.get("financialTies"))) flags.add("FINANCIAL_TIES");
        return flags;
    }

    // ---- helpers ----

    private void requireNoBlockingFlags(ProviderApplication application) {
        List<String> blocking = flags(application, answerStore.readAll(application)).stream().filter(BLOCKING_FLAGS::contains).toList();
        if (!blocking.isEmpty()) {
            throw new ProviderOnboardingException(409, "APPLICATION_NOT_APPROVABLE", "This application can't move forward: " + String.join(", ", blocking),
                    Map.of("flags", blocking));
        }
    }

    private Long requireIndependentReviewer(ProviderApplication application) {
        Long reviewer = authorizationService.requireUserId();
        if (reviewer.equals(application.getUserId())) {
            throw new ProviderOnboardingException(409, "SAME_REVIEWER", "You can't review your own application.");
        }
        return reviewer;
    }

    private void recordReview(ProviderApplication application, ReviewStage stage, Long reviewer, ReviewDecision decision, String reason, Map<String, Boolean> checklist) {
        ApplicationReview review = new ApplicationReview();
        review.setApplicationId(application.getId());
        review.setStage(stage);
        review.setReviewerUserId(reviewer);
        review.setDecision(decision);
        review.setReason(reason);
        try {
            review.setChecklistJson(checklist == null ? null : objectMapper.writeValueAsString(checklist));
        } catch (JsonProcessingException e) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "The checklist could not be read.");
        }
        reviewRepository.save(review);
        auditService.record("PROVIDER_APPLICATION_REVIEWED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), reason,
                Map.of("stage", stage.name(), "decision", decision.name()));
    }

    private Map<String, Boolean> checklist(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static LocalDate licenceExpiry(Map<String, String> answers) {
        String value = answers.get(ApplicationFormCatalog.KEY_REGISTRATION_EXPIRY);
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<String> stages(ProviderApplication application) {
        return application.getClearedStages() == null ? List.of() : Arrays.asList(application.getClearedStages().split(","));
    }

    private List<ReviewAnswer> reviewAnswers(FormSection section, Map<String, String> answers, Map<String, FormQuestion> byKey) {
        return section.questions().stream()
                .filter(question -> question.type() != QuestionType.FILE && catalog.isVisible(question, answers, byKey))
                .map(question -> new ReviewAnswer(question.key(), question.label(), displayValue(question, answers.get(question.key()))))
                .toList();
    }

    private static String displayValue(FormQuestion question, String stored) {
        if (stored == null) {
            return null;
        }
        if (question.type() == QuestionType.CONFIRM) {
            return "Confirmed";
        }
        if (question.options().isEmpty()) {
            return stored;
        }
        return ApplicationAnswerStore.splitMulti(stored).stream().map(value -> question.options().stream()
                .filter(option -> option.value().equals(value)).map(FormOption::label).findFirst().orElse(value))
                .collect(Collectors.joining(", "));
    }

    private ProviderApplication requireStatus(Long id, ApplicationStatus status) {
        ProviderApplication application = find(id);
        if (application.getStatus() != status) {
            throw new ProviderOnboardingException(409, "WRONG_REVIEW_STAGE", "This application is at " + application.getStatus() + ", not " + status + ".");
        }
        return application;
    }

    private ProviderApplication find(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ProviderOnboardingException(404, "APPLICATION_NOT_FOUND", "Application not found."));
    }
}
