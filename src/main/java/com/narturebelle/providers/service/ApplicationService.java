package com.narturebelle.providers.service;

import com.narturebelle.providers.dto.ApplicationDtos.ApplicationResponse;
import com.narturebelle.providers.dto.ApplicationDtos.DocumentResponse;
import com.narturebelle.providers.dto.ApplicationDtos.EntryResponse;
import com.narturebelle.providers.dto.ApplicationDtos.MissingAnswer;
import com.narturebelle.providers.dto.ApplicationDtos.RequestedChangeResponse;
import com.narturebelle.providers.dto.ApplicationDtos.StartApplicationRequest;
import com.narturebelle.providers.dto.FormDtos.ApplyInfoResponse;
import com.narturebelle.providers.dto.FormDtos.FormQuestion;
import com.narturebelle.providers.dto.FormDtos.FormSection;
import com.narturebelle.providers.dto.FormDtos.ProfessionOption;
import com.narturebelle.providers.dto.FormDtos.QuestionType;
import com.narturebelle.providers.exception.ProviderOnboardingException;
import com.narturebelle.providers.model.ApplicationDocument;
import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.providers.model.RequestedChange;
import com.narturebelle.model.Role;
import com.narturebelle.providers.repository.ProviderApplicationRepository;
import com.narturebelle.providers.repository.RequestedChangeRepository;
import com.narturebelle.repository.OnboardingIntakeRepository;
import com.narturebelle.repository.JourneyRepository;
import com.narturebelle.repository.OrderRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** The applicant's side: starting, answering, submitting, withdrawing. Anyone can apply; approval is a separate step. */
@Service
public class ApplicationService {

    private static final int MAX_TEXT = 2000;

    private final ProviderApplicationRepository applicationRepository;
    private final RequestedChangeRepository requestedChangeRepository;
    private final ApplicationAnswerStore answerStore;
    private final ApplicationDocumentService documentService;
    private final ApplicationFormCatalog catalog;
    private final AgreementCatalog agreementCatalog;
    private final JourneyRepository journeyRepository;
    private final OrderRepository orderRepository;
    private final OnboardingIntakeRepository intakeRepository;
    private final ProviderRepository providerRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final ApplicationNotifier notifier;
    private final boolean requirePhone;

    public ApplicationService(ProviderApplicationRepository applicationRepository, RequestedChangeRepository requestedChangeRepository,
                              ApplicationAnswerStore answerStore, ApplicationDocumentService documentService,
                              ApplicationFormCatalog catalog, AgreementCatalog agreementCatalog,
                              JourneyRepository journeyRepository, OrderRepository orderRepository,
                              OnboardingIntakeRepository intakeRepository, ProviderRepository providerRepository,
                              AuthorizationService authorizationService, AuditService auditService, ApplicationNotifier notifier,
                              @Value("${onboarding.require-phone-verification}") boolean requirePhone) {
        this.applicationRepository = applicationRepository;
        this.requestedChangeRepository = requestedChangeRepository;
        this.answerStore = answerStore;
        this.documentService = documentService;
        this.catalog = catalog;
        this.agreementCatalog = agreementCatalog;
        this.journeyRepository = journeyRepository;
        this.orderRepository = orderRepository;
        this.intakeRepository = intakeRepository;
        this.providerRepository = providerRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.notifier = notifier;
        this.requirePhone = requirePhone;
    }

    // ---- reads ----

    public ApplyInfoResponse applyInfo() {
        List<ProfessionOption> professions = ApplicationFormCatalog.PROFESSIONS.entrySet().stream()
                .map(e -> new ProfessionOption(e.getKey().name(), e.getValue())).toList();
        return new ApplyInfoResponse(professions, AgreementCatalog.TERMS_VERSION, agreementCatalog.termsText(), AgreementCatalog.IS_DRAFT);
    }

    public EntryResponse entry() {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        Long userId = principal.userId();
        Optional<ProviderApplication> latest = userId == null ? Optional.empty() : applicationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        boolean mother = userId != null && isMotherAccount(userId);
        boolean blocked = userId != null && applicationRepository.existsByUserIdAndFraudBlockedTrue(userId);
        return new EntryResponse(latest.isPresent(), latest.map(ProviderApplication::getStatus).orElse(null), mother, blocked,
                latest.map(ProviderApplication::getReapplyAfter).orElse(null), principal.emailVerified(), principal.phone() != null);
    }

    public List<FormSection> form(Role profession) {
        requireProfession(profession);
        return catalog.sections(profession);
    }

    public ProviderApplication requireCurrent() {
        return applicationRepository.findFirstByUserIdOrderByCreatedAtDesc(authorizationService.requireUserId())
                .orElseThrow(() -> new ProviderOnboardingException(404, "APPLICATION_NOT_FOUND", "You haven't started an application."));
    }

    public ApplicationResponse current() {
        return toResponse(requireCurrent());
    }

    // ---- writes ----

    @Transactional
    public ApplicationResponse start(StartApplicationRequest req) {
        requireProfession(req.profession());
        Long userId = authorizationService.requireUserId();
        if (applicationRepository.existsByUserIdAndFraudBlockedTrue(userId)) {
            throw new ProviderOnboardingException(403, "APPLICATION_BLOCKED", "This account can't apply. Please contact support.");
        }
        Optional<ProviderApplication> latest = applicationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (latest.isPresent() && !latest.get().getStatus().isClosed()) {
            throw new ProviderOnboardingException(409, "APPLICATION_EXISTS", "You already have an application.");
        }
        if (latest.isPresent() && latest.get().getReapplyAfter() != null && latest.get().getReapplyAfter().isAfter(LocalDate.now())) {
            throw new ProviderOnboardingException(409, "REAPPLY_NOT_YET", "You can apply again from " + latest.get().getReapplyAfter() + ".",
                    Map.of("reapplyAfter", latest.get().getReapplyAfter().toString()));
        }
        if (isMotherAccount(userId)) {
            throw new ProviderOnboardingException(409, "MOTHER_ACCOUNT", "Please apply with a different email.");
        }
        if (providerRepository.findByUserId(userId).isPresent()) {
            throw new ProviderOnboardingException(409, "ALREADY_PROFESSIONAL", "This account is already a NartureBelle professional.");
        }
        ProviderApplication application = new ProviderApplication();
        application.setUserId(userId);
        application.setProfessionalType(req.profession());
        application.setStatus(ApplicationStatus.DRAFT);
        application.setTermsVersion(AgreementCatalog.TERMS_VERSION);
        application.setTermsAcceptedAt(LocalDateTime.now());
        ProviderApplication saved = applicationRepository.save(application);
        auditService.record("PROVIDER_APPLICATION_STARTED", "PROVIDER_APPLICATION", String.valueOf(saved.getId()), null,
                Map.of("profession", req.profession().name()));
        // The professional's sign-up welcome: accepting the application terms is their first step after sign-up.
        // First application only, so reapplying after a withdrawal or rejection sends nothing new.
        if (latest.isEmpty()) {
            notifier.notifyUser("PROVIDER_SIGNUP_WELCOME", userId, "Welcome to NartureBelle, let's complete your application",
                    "Thank you for applying to care for mothers with NartureBelle. Your answers save as you go, so you can "
                            + "finish your application at any time: https://www.narturebelle.com/provider/apply");
        }
        return toResponse(saved);
    }

    /** Before submitting only. Answers to the old profession's own questions, and their documents, are cleared. */
    @Transactional
    public ApplicationResponse changeProfession(Role profession) {
        requireProfession(profession);
        ProviderApplication application = requireCurrent();
        if (application.getStatus() != ApplicationStatus.DRAFT) {
            throw new ProviderOnboardingException(409, "APPLICATION_LOCKED", "Your profession can only be changed before you submit.");
        }
        if (application.getProfessionalType() != profession) {
            Set<String> commonKeys = catalog.sections(profession).stream()
                    .filter(section -> !section.key().equals("professional"))
                    .flatMap(section -> section.questions().stream()).map(FormQuestion::key).collect(Collectors.toSet());
            for (FormSection section : catalog.sections(application.getProfessionalType())) {
                if (section.key().equals("professional")) {
                    for (FormQuestion question : section.questions()) {
                        if (!commonKeys.contains(question.key())) {
                            answerStore.remove(application.getId(), question.key());
                            documentService.remove(application.getId(), question.key());
                        }
                    }
                }
            }
            answerStore.remove(application.getId(), "careStages");
            application.setProfessionalType(profession);
            application.setLastActivityAt(LocalDateTime.now());
            applicationRepository.save(application);
        }
        return toResponse(application);
    }

    /** Autosave: partial answers, validated but not checked for completeness. A blank value clears the answer. */
    @Transactional
    public ApplicationResponse saveAnswers(Map<String, Object> answers) {
        ProviderApplication application = requireEditable();
        Map<String, FormQuestion> byKey = catalog.questionsByKey(application.getProfessionalType());
        Set<String> unlocked = requestedChangeRepository.findByApplicationId(application.getId()).stream()
                .map(RequestedChange::getQuestionKey).collect(Collectors.toSet());

        Map<String, String> errors = new HashMap<>();
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : answers.entrySet()) {
            FormQuestion question = byKey.get(entry.getKey());
            if (question == null || question.type() == QuestionType.FILE) {
                errors.put(entry.getKey(), "That isn't a question on this form.");
                continue;
            }
            if (application.getStatus() == ApplicationStatus.CHANGES_REQUESTED && !unlocked.contains(entry.getKey())) {
                errors.put(entry.getKey(), "Only the fields our reviewers asked about can be changed now.");
                continue;
            }
            String value = normalize(question, entry.getValue());
            String problem = value.isEmpty() ? null : problemWith(question, value);
            if (problem != null) {
                errors.put(entry.getKey(), problem);
            } else {
                normalized.put(entry.getKey(), value);
            }
        }
        if (!errors.isEmpty()) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please check the highlighted fields.", Map.of("fields", errors));
        }
        normalized.forEach((key, value) -> {
            if (value.isEmpty()) {
                answerStore.remove(application.getId(), key);
            } else {
                answerStore.put(application.getId(), key, value, byKey.get(key).sensitive());
            }
        });
        application.setLastActivityAt(LocalDateTime.now());
        applicationRepository.save(application);
        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse submit() {
        ProviderApplication application = requireEditable();
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        if (!isContactVerified(principal)) {
            throw new ProviderOnboardingException(409, "CONTACT_NOT_VERIFIED", requirePhone
                    ? "Please verify your email and phone before submitting."
                    : "Please verify your email before submitting.");
        }
        Map<String, FormQuestion> byKey = catalog.questionsByKey(application.getProfessionalType());
        Map<String, String> answers = answerStore.readAll(application);
        Set<String> uploaded = documentService.list(application.getId()).stream()
                .map(ApplicationDocument::getQuestionKey).collect(Collectors.toSet());

        List<MissingAnswer> missing = missingAnswers(application.getProfessionalType(), answers, uploaded, byKey);
        if (!missing.isEmpty()) {
            throw new ProviderOnboardingException(409, "APPLICATION_INCOMPLETE", "Please complete the remaining questions.", Map.of("missing", missing));
        }
        // Answers left behind when the applicant changed a parent question aren't part of what they submit.
        for (FormQuestion question : byKey.values()) {
            if (!catalog.isVisible(question, answers, byKey)) {
                answerStore.remove(application.getId(), question.key());
                documentService.remove(application.getId(), question.key());
            }
        }
        requestedChangeRepository.deleteByApplicationId(application.getId());

        // Read after the removal above: a number left behind by a changed parent answer isn't theirs to claim.
        Map<String, String> submitted = answerStore.readAll(application);
        String number = submitted.getOrDefault(ApplicationFormCatalog.KEY_REGISTRATION_NUMBER, submitted.get(ApplicationFormCatalog.KEY_ALLIED_HEALTH_ID));
        application.setRegistrationNumber(number == null ? null : number.replaceAll("\\s+", "").toUpperCase(Locale.ROOT));
        application.setStatus(ApplicationStatus.OPERATIONS_REVIEW);
        application.setSubmittedAt(LocalDateTime.now());
        application.setLastActivityAt(LocalDateTime.now());
        applicationRepository.save(application);

        auditService.record("PROVIDER_APPLICATION_SUBMITTED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), null);
        notifier.notifyUser(application.getUserId(), "We received your NartureBelle application",
                "Thank you for applying. Our team will check your details and let you know the outcome.");
        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse withdraw() {
        ProviderApplication application = requireCurrent();
        if (application.getStatus().isClosed() || application.getStatus().isApproved()) {
            throw new ProviderOnboardingException(409, "APPLICATION_LOCKED", "This application can no longer be withdrawn.");
        }
        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.setClosedAt(LocalDateTime.now());
        applicationRepository.save(application);
        auditService.record("PROVIDER_APPLICATION_WITHDRAWN", "PROVIDER_APPLICATION", String.valueOf(application.getId()), null);
        return toResponse(application);
    }

    public ProviderApplication requireEditable() {
        ProviderApplication application = requireCurrent();
        if (!application.getStatus().isEditable()) {
            throw new ProviderOnboardingException(409, "APPLICATION_LOCKED", "This application can no longer be changed.");
        }
        return application;
    }

    // ---- helpers ----

    private List<MissingAnswer> missingAnswers(Role profession, Map<String, String> answers, Collection<String> uploaded, Map<String, FormQuestion> byKey) {
        List<MissingAnswer> missing = new ArrayList<>();
        for (FormSection section : catalog.sections(profession)) {
            for (FormQuestion question : section.questions()) {
                if (!question.required() || !catalog.isVisible(question, answers, byKey)) {
                    continue;
                }
                boolean answered = question.type() == QuestionType.FILE
                        ? uploaded.contains(question.key())
                        : answers.get(question.key()) != null && !answers.get(question.key()).isBlank();
                if (!answered) {
                    missing.add(new MissingAnswer(question.key(), question.label(), section.title()));
                }
            }
        }
        return missing;
    }

    private boolean isMotherAccount(Long userId) {
        return journeyRepository.existsByUserId(userId) || orderRepository.existsByUserId(userId) || intakeRepository.existsByUserId(userId);
    }

    /** A verified email; a verified phone too only when onboarding.require-phone-verification is on (off by default). */
    private boolean isContactVerified(FirebaseUserPrincipal principal) {
        return principal.emailVerified() && (!requirePhone || principal.phone() != null);
    }

    private static void requireProfession(Role profession) {
        if (!ApplicationFormCatalog.PROFESSIONS.containsKey(profession)) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please choose a listed profession.");
        }
    }

    private static String normalize(FormQuestion question, Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.joining("\n"));
        }
        String value = String.valueOf(raw).trim();
        if (question.key().equals("pan")) {
            return value.toUpperCase(Locale.ROOT);
        }
        if (question.type() == QuestionType.CONFIRM && "false".equals(value)) {
            return "";
        }
        return value;
    }

    private static String problemWith(FormQuestion question, String value) {
        if (value.length() > MAX_TEXT) {
            return "Please keep this under " + MAX_TEXT + " characters.";
        }
        if (question.pattern() != null && !value.matches(question.pattern())) {
            return question.patternMessage();
        }
        switch (question.type()) {
            case NUMBER -> {
                if (!value.matches("^[0-9]{1,6}$")) return "Please enter a whole number.";
            }
            case YEAR -> {
                if (!value.matches("^[0-9]{4}$") || Integer.parseInt(value) < 1950 || Integer.parseInt(value) > Year.now().getValue()) {
                    return "Please enter a valid year.";
                }
            }
            case DATE -> {
                try {
                    LocalDate date = LocalDate.parse(value);
                    if (question.key().equals("dateOfBirth") && !date.isBefore(LocalDate.now())) return "Please enter a past date.";
                } catch (DateTimeParseException e) {
                    return "Please enter a valid date.";
                }
            }
            case YES_NO, SELECT -> {
                if (question.options().stream().noneMatch(option -> option.value().equals(value))) return "Please choose one of the options.";
            }
            case MULTI_SELECT -> {
                for (String part : value.split("\n")) {
                    if (question.options().stream().noneMatch(option -> option.value().equals(part))) return "Please choose from the options.";
                }
            }
            case CONFIRM -> {
                if (!"true".equals(value)) return "Please confirm this.";
            }
            default -> { }
        }
        return null;
    }

    public ApplicationResponse toResponse(ProviderApplication application) {
        Map<String, FormQuestion> byKey = catalog.questionsByKey(application.getProfessionalType());
        Map<String, Object> answers = new LinkedHashMap<>();
        List<String> sensitiveSaved = new ArrayList<>();
        answerStore.readAll(application).forEach((key, value) -> {
            FormQuestion question = byKey.get(key);
            if (question != null && question.sensitive()) {
                sensitiveSaved.add(key);
            } else {
                answers.put(key, ApplicationAnswerStore.toApiValue(question, value));
            }
        });
        List<DocumentResponse> documents = documentService.list(application.getId()).stream()
                .filter(document -> !document.getQuestionKey().startsWith(ApplicationDocumentService.PENDING_PREFIX))
                .map(documentService::toResponse).toList();
        List<RequestedChangeResponse> changes = requestedChangeRepository.findByApplicationId(application.getId()).stream()
                .map(change -> new RequestedChangeResponse(change.getQuestionKey(), change.getNote())).toList();
        return new ApplicationResponse(application.getId(), application.getProfessionalType(), application.getStatus(), answers, sensitiveSaved,
                documents, changes, application.getSubmittedAt(), application.getRejectionReason(), application.getReapplyAfter(),
                application.getVerifiedAt(), application.getActivatedAt(), application.isCanPrescribe(), application.getConfirmedExperienceYears(),
                application.getProviderId(), application.getLicenceExpiresOn(), application.isLicenceLapsed(),
                isContactVerified(authorizationService.currentPrincipal()));
    }
}
