package com.narturebelle.providers.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.providers.dto.ReviewDtos.ChangeRequestForReview;
import com.narturebelle.providers.dto.SetupDtos.BankDetailsRequest;
import com.narturebelle.providers.dto.WorkspaceDtos.ChangeRequestResponse;
import com.narturebelle.providers.exception.ProviderOnboardingException;
import com.narturebelle.providers.model.ApplicationDocument;
import com.narturebelle.providers.model.BankAccountType;
import com.narturebelle.providers.model.ProfileChangeRequest;
import com.narturebelle.providers.model.ProfileChangeStatus;
import com.narturebelle.providers.model.ProfileChangeType;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.providers.model.ProviderBankDetails;
import com.narturebelle.model.Role;
import com.narturebelle.model.Provider;
import com.narturebelle.providers.repository.ApplicationDocumentRepository;
import com.narturebelle.providers.repository.ProfileChangeRequestRepository;
import com.narturebelle.providers.repository.ProviderApplicationRepository;
import com.narturebelle.providers.repository.ProviderBankDetailsRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.providers.security.FieldEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A professional's change after approval. The old details stay in place until staff approve it. */
@Service
public class ProfileChangeService {

    private static final int MAX_NAME = 160;
    private static final int MAX_TEXT = 2000;

    private static final Map<ProfileChangeType, Set<String>> ALLOWED_VALUES = Map.of(
            ProfileChangeType.PROFILE, Set.of("displayName", "bio", "city", "languages"),
            ProfileChangeType.NAME, Set.of("legalName"),
            ProfileChangeType.LICENCE, Set.of("registrationNumber", "registrationExpiry"),
            ProfileChangeType.BANK, Set.of("accountHolderName", "accountNumber", "accountNumberConfirm", "ifsc", "accountType", "gstRegistered", "gstin"));

    private final ProfileChangeRequestRepository changeRepository;
    private final ProviderApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final ApplicationSetupService setupService;
    private final ApplicationAnswerStore answerStore;
    private final ApplicationDocumentService documentService;
    private final ApplicationDocumentRepository documentRepository;
    private final ProviderBankDetailsRepository bankRepository;
    private final ProviderRepository providerRepository;
    private final ProviderLifecycleService lifecycleService;
    private final FieldEncryptor encryptor;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final AuthorizationService authorizationService;
    private final ApplicationNotifier notifier;

    public ProfileChangeService(ProfileChangeRequestRepository changeRepository, ProviderApplicationRepository applicationRepository,
                                ApplicationService applicationService, ApplicationSetupService setupService, ApplicationAnswerStore answerStore,
                                ApplicationDocumentService documentService, ApplicationDocumentRepository documentRepository,
                                ProviderBankDetailsRepository bankRepository, ProviderRepository providerRepository,
                                ProviderLifecycleService lifecycleService, FieldEncryptor encryptor, ObjectMapper objectMapper,
                                AuditService auditService, AuthorizationService authorizationService, ApplicationNotifier notifier) {
        this.changeRepository = changeRepository;
        this.applicationRepository = applicationRepository;
        this.applicationService = applicationService;
        this.setupService = setupService;
        this.answerStore = answerStore;
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.bankRepository = bankRepository;
        this.providerRepository = providerRepository;
        this.lifecycleService = lifecycleService;
        this.encryptor = encryptor;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.authorizationService = authorizationService;
        this.notifier = notifier;
    }

    // ---- professional ----

    public List<ChangeRequestResponse> mine() {
        return changeRepository.findByApplicationIdOrderByCreatedAtDesc(requireApproved().getId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ChangeRequestResponse request(ProfileChangeType type, Map<String, Object> values) {
        ProviderApplication application = requireApproved();
        if (changeRepository.existsByApplicationIdAndChangeTypeAndStatus(application.getId(), type, ProfileChangeStatus.PENDING)) {
            throw new ProviderOnboardingException(409, "CHANGE_ALREADY_PENDING", "You already have a change like this waiting for review.");
        }
        if (!ALLOWED_VALUES.get(type).containsAll(values.keySet()) || values.isEmpty()) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Those details can't be changed here.");
        }
        validate(application, type, values);
        ProfileChangeRequest change = new ProfileChangeRequest();
        change.setApplicationId(application.getId());
        change.setChangeType(type);
        change.setPayloadEncrypted(encryptor.encrypt(toJson(values)));
        change.setStatus(ProfileChangeStatus.PENDING);
        ProfileChangeRequest saved = changeRepository.save(change);
        auditService.record("PROVIDER_CHANGE_REQUESTED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), null, Map.of("type", type.name()));
        return toResponse(saved);
    }

    /** The renewed certificate or name proof for a pending change. */
    @Transactional
    public ChangeRequestResponse attachDocument(Long changeId, MultipartFile file) {
        ProviderApplication application = requireApproved();
        ProfileChangeRequest change = changeRepository.findById(changeId)
                .filter(found -> found.getApplicationId().equals(application.getId()) && found.getStatus() == ProfileChangeStatus.PENDING)
                .orElseThrow(() -> new ProviderOnboardingException(404, "CHANGE_NOT_FOUND", "Change request not found."));
        String key = documentKey(change.getChangeType());
        if (key == null) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "This change doesn't need a document.");
        }
        documentService.storePending(application.getId(), key, file);
        return toResponse(change);
    }

    // ---- staff ----

    public List<ChangeRequestForReview> pendingForReview() {
        return changeRepository.findByStatusOrderByCreatedAtAsc(ProfileChangeStatus.PENDING).stream().map(this::toReview).toList();
    }

    public List<ChangeRequestForReview> forApplication(Long applicationId) {
        return changeRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream().map(this::toReview).toList();
    }

    @Transactional
    public ChangeRequestForReview decide(Long changeId, boolean approve, String reason) {
        ProfileChangeRequest change = changeRepository.findById(changeId)
                .filter(found -> found.getStatus() == ProfileChangeStatus.PENDING)
                .orElseThrow(() -> new ProviderOnboardingException(404, "CHANGE_NOT_FOUND", "Change request not found."));
        ProviderApplication application = applicationRepository.findById(change.getApplicationId())
                .orElseThrow(() -> new ProviderOnboardingException(404, "APPLICATION_NOT_FOUND", "Application not found."));
        // A renewed licence is a clinical matter too; everything else, bank details above all, is Operations' alone.
        if (change.getChangeType() != ProfileChangeType.LICENCE) {
            authorizationService.requireRole(Role.OPERATIONS_ADMIN);
        }
        if (authorizationService.requireUserId().equals(application.getUserId())) {
            throw new ProviderOnboardingException(409, "SAME_REVIEWER", "You can't review your own change.");
        }
        String key = documentKey(change.getChangeType());
        if (approve) {
            if (key != null && documentRepository.findByApplicationIdAndQuestionKey(application.getId(), ApplicationDocumentService.PENDING_PREFIX + key).isEmpty()) {
                throw new ProviderOnboardingException(409, "CHANGE_DOCUMENT_MISSING", "This change has no supporting document yet.");
            }
            applyChange(application, change.getChangeType(), payload(change));
        } else if (key != null) {
            documentService.remove(application.getId(), ApplicationDocumentService.PENDING_PREFIX + key);
        }
        change.setStatus(approve ? ProfileChangeStatus.APPROVED : ProfileChangeStatus.REJECTED);
        change.setReviewerUserId(authorizationService.requireUserId());
        change.setDecisionReason(reason);
        change.setDecidedAt(LocalDateTime.now());
        changeRepository.save(change);
        auditService.record(approve ? "PROVIDER_CHANGE_APPROVED" : "PROVIDER_CHANGE_REJECTED", "PROVIDER_APPLICATION",
                String.valueOf(application.getId()), reason, Map.of("type", change.getChangeType().name()));
        notifier.notifyUser(application.getUserId(), "Your NartureBelle profile change",
                approve ? "Your requested change has been approved." : "Your requested change wasn't approved." + (reason == null ? "" : " Reason: " + reason));
        return toReview(change);
    }

    // ---- helpers ----

    private void applyChange(ProviderApplication application, ProfileChangeType type, Map<String, Object> values) {
        switch (type) {
            case PROFILE -> {
                Provider provider = providerRepository.findById(application.getProviderId())
                        .orElseThrow(() -> new IllegalStateException("Application references a missing provider."));
                if (values.containsKey("displayName")) provider.setDisplayName(String.valueOf(values.get("displayName")));
                if (values.containsKey("bio")) provider.setBio(String.valueOf(values.get("bio")));
                if (values.containsKey("city")) provider.setCity(String.valueOf(values.get("city")));
                if (values.get("languages") instanceof List<?> languages) {
                    provider.setLanguages(new ArrayList<>(languages.stream().map(String::valueOf).toList()));
                }
                providerRepository.save(provider);
            }
            case NAME -> {
                answerStore.put(application.getId(), ApplicationFormCatalog.KEY_LEGAL_NAME, String.valueOf(values.get("legalName")), false);
                documentService.promotePending(application.getId(), ApplicationFormCatalog.KEY_NAME_PROOF);
            }
            case LICENCE -> {
                if (values.containsKey("registrationNumber")) {
                    String number = String.valueOf(values.get("registrationNumber"));
                    // Some professions hold a national allied health ID instead of a council number.
                    boolean usesAlliedId = answerStore.read(application, ApplicationFormCatalog.KEY_REGISTRATION_NUMBER) == null
                            && answerStore.read(application, ApplicationFormCatalog.KEY_ALLIED_HEALTH_ID) != null;
                    answerStore.put(application.getId(), usesAlliedId ? ApplicationFormCatalog.KEY_ALLIED_HEALTH_ID
                            : ApplicationFormCatalog.KEY_REGISTRATION_NUMBER, number, false);
                    application.setRegistrationNumber(number.replaceAll("\\s+", "").toUpperCase(Locale.ROOT));
                }
                if (values.containsKey("registrationExpiry")) {
                    String expiry = String.valueOf(values.get("registrationExpiry"));
                    answerStore.put(application.getId(), ApplicationFormCatalog.KEY_REGISTRATION_EXPIRY, expiry, false);
                    application.setLicenceExpiresOn(LocalDate.parse(expiry));
                }
                documentService.promotePending(application.getId(), ApplicationFormCatalog.KEY_REGISTRATION_CERTIFICATE);
                lifecycleService.restoreAfterLicenceRenewal(application);
            }
            case BANK -> {
                ProviderBankDetails bank = setupService.applyBank(application.getId(), bankRequest(values));
                bankRepository.save(bank);
            }
        }
    }

    private void validate(ProviderApplication application, ProfileChangeType type, Map<String, Object> values) {
        switch (type) {
            case BANK -> setupService.validateBank(bankRequest(values));
            case NAME -> requireText(values, "legalName", MAX_NAME, true);
            case PROFILE -> {
                requireText(values, "displayName", MAX_NAME, false);
                requireText(values, "bio", MAX_TEXT, false);
                requireText(values, "city", MAX_NAME, false);
                if (values.containsKey("languages")) {
                    boolean valid = values.get("languages") instanceof List<?> languages && !languages.isEmpty()
                            && languages.stream().allMatch(language -> language instanceof String text && !text.isBlank() && text.length() <= MAX_NAME);
                    if (!valid) {
                        throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please list at least one language.");
                    }
                }
            }
            case LICENCE -> {
                requireText(values, "registrationNumber", MAX_NAME, false);
                // A date already on file, or a lapse, can only be cleared by a new date.
                if (!values.containsKey("registrationExpiry") && (application.getLicenceExpiresOn() != null || application.isLicenceLapsed())) {
                    throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please enter the new expiry date.");
                }
                if (values.containsKey("registrationExpiry")) {
                    try {
                        if (!LocalDate.parse(String.valueOf(values.get("registrationExpiry"))).isAfter(LocalDate.now())) {
                            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "The new expiry date must be in the future.");
                        }
                    } catch (DateTimeParseException e) {
                        throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please enter a valid date.");
                    }
                }
            }
        }
    }

    /** A value that's present must be real text: String.valueOf would otherwise store a missing one as the word "null". */
    private static void requireText(Map<String, Object> values, String key, int max, boolean required) {
        Object value = values.get(key);
        if (value == null && !required && !values.containsKey(key)) {
            return;
        }
        if (!(value instanceof String text) || text.isBlank() || text.length() > max) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please check the value for " + key + ".");
        }
    }

    private BankDetailsRequest bankRequest(Map<String, Object> values) {
        try {
            requireText(values, "accountHolderName", MAX_NAME, true);
            String number = String.valueOf(values.get("accountNumber"));
            String ifsc = String.valueOf(values.get("ifsc")).toUpperCase(Locale.ROOT);
            if (!number.matches("^[0-9]{9,18}$") || !ifsc.matches("^[A-Z]{4}0[A-Z0-9]{6}$")) {
                throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please check your account number and IFSC.");
            }
            Object gstin = values.get("gstin");
            if (gstin != null && !String.valueOf(gstin).isBlank()
                    && !String.valueOf(gstin).matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$")) {
                throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please enter a valid GSTIN.");
            }
            return new BankDetailsRequest(String.valueOf(values.get("accountHolderName")), number, String.valueOf(values.get("accountNumberConfirm")),
                    ifsc, BankAccountType.valueOf(String.valueOf(values.get("accountType"))),
                    Boolean.parseBoolean(String.valueOf(values.get("gstRegistered"))), gstin == null ? null : String.valueOf(gstin));
        } catch (IllegalArgumentException e) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please choose a valid account type.");
        }
    }

    private static String documentKey(ProfileChangeType type) {
        return switch (type) {
            case NAME -> ApplicationFormCatalog.KEY_NAME_PROOF;
            case LICENCE -> ApplicationFormCatalog.KEY_REGISTRATION_CERTIFICATE;
            default -> null;
        };
    }

    private ProviderApplication requireApproved() {
        ProviderApplication application = applicationService.requireCurrent();
        if (!application.getStatus().isApproved()) {
            throw new ProviderOnboardingException(403, "NOT_APPROVED", "Your dashboard opens once your application is approved.");
        }
        return application;
    }

    private ChangeRequestForReview toReview(ProfileChangeRequest change) {
        String key = documentKey(change.getChangeType());
        Long documentId = key == null ? null : documentRepository
                .findByApplicationIdAndQuestionKey(change.getApplicationId(), ApplicationDocumentService.PENDING_PREFIX + key)
                .map(ApplicationDocument::getId).orElse(null);
        return new ChangeRequestForReview(change.getId(), change.getApplicationId(), change.getChangeType(), change.getStatus(),
                visibleValues(change), documentId, change.getCreatedAt());
    }

    /** Full account numbers go to Operations alone, who act on them. The repeat of the number is never shown. */
    private Map<String, Object> visibleValues(ProfileChangeRequest change) {
        Map<String, Object> values = new HashMap<>(payload(change));
        values.remove("accountNumberConfirm");
        if (values.get("accountNumber") instanceof String number && !authorizationService.hasRole(Role.OPERATIONS_ADMIN)) {
            values.put("accountNumber", "ending " + number.substring(Math.max(0, number.length() - 4)));
        }
        return values;
    }

    private Map<String, Object> payload(ProfileChangeRequest change) {
        try {
            return objectMapper.readValue(encryptor.decrypt(change.getPayloadEncrypted()), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read a change request.", e);
        }
    }

    private String toJson(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store a change request.", e);
        }
    }

    private ChangeRequestResponse toResponse(ProfileChangeRequest change) {
        String key = documentKey(change.getChangeType());
        boolean attached = key != null && documentRepository
                .findByApplicationIdAndQuestionKey(change.getApplicationId(), ApplicationDocumentService.PENDING_PREFIX + key).isPresent();
        return new ChangeRequestResponse(change.getId(), change.getChangeType(), change.getStatus(), change.getDecisionReason(),
                change.getCreatedAt(), change.getDecidedAt(), key != null, attached);
    }
}
