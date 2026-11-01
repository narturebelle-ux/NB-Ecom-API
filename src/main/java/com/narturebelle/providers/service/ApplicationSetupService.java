package com.narturebelle.providers.service;

import com.narturebelle.providers.dto.SetupDtos.AgreementResponse;
import com.narturebelle.providers.dto.SetupDtos.BankDetailsRequest;
import com.narturebelle.providers.dto.SetupDtos.BankDetailsResponse;
import com.narturebelle.providers.dto.SetupDtos.SetupStatusResponse;
import com.narturebelle.providers.dto.SetupDtos.TrainingResponse;
import com.narturebelle.providers.dto.SetupDtos.TrainingResult;
import com.narturebelle.providers.exception.ProviderOnboardingException;
import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.providers.model.BankVerificationStatus;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.providers.model.ProviderBankDetails;
import com.narturebelle.model.Role;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.providers.repository.ProviderApplicationRepository;
import com.narturebelle.providers.repository.ProviderBankDetailsRepository;
import com.narturebelle.repository.PayoutRateRepository;
import com.narturebelle.repository.ProviderSlotRepository;
import com.narturebelle.repository.ServiceOfferingRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.providers.security.FieldEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What a professional finishes after approval and before going live. */
@Service
public class ApplicationSetupService {

    private final ApplicationService applicationService;
    private final ProviderApplicationRepository applicationRepository;
    private final ProviderBankDetailsRepository bankRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final ProviderSlotRepository slotRepository;
    private final PayoutRateRepository payoutRateRepository;
    private final ApplicationAnswerStore answerStore;
    private final AgreementCatalog agreementCatalog;
    private final TrainingCatalog trainingCatalog;
    private final FieldEncryptor encryptor;
    private final AuditService auditService;
    private final boolean signingEnabled;

    public ApplicationSetupService(ApplicationService applicationService, ProviderApplicationRepository applicationRepository,
                                   ProviderBankDetailsRepository bankRepository, ServiceOfferingRepository serviceRepository,
                                   ProviderSlotRepository slotRepository, PayoutRateRepository payoutRateRepository,
                                   ApplicationAnswerStore answerStore, AgreementCatalog agreementCatalog, TrainingCatalog trainingCatalog,
                                   FieldEncryptor encryptor, AuditService auditService,
                                   @Value("${onboarding.agreement-signing-enabled}") boolean signingEnabled) {
        this.applicationService = applicationService;
        this.applicationRepository = applicationRepository;
        this.bankRepository = bankRepository;
        this.serviceRepository = serviceRepository;
        this.slotRepository = slotRepository;
        this.payoutRateRepository = payoutRateRepository;
        this.answerStore = answerStore;
        this.agreementCatalog = agreementCatalog;
        this.trainingCatalog = trainingCatalog;
        this.encryptor = encryptor;
        this.auditService = auditService;
        this.signingEnabled = signingEnabled;
    }

    public SetupStatusResponse status() {
        return status(requireInSetupOrLater());
    }

    public SetupStatusResponse status(ProviderApplication application) {
        ProviderBankDetails bank = bankRepository.findById(application.getId()).orElse(null);
        boolean agreement = application.getAgreementSignedAt() != null;
        boolean bankPassed = bank != null && bank.getVerificationStatus() == BankVerificationStatus.PASSED;
        boolean training = application.getTrainingPassedAt() != null;
        boolean services = application.getProviderId() != null
                && serviceRepository.findByProviderIdOrderByCreatedAtDesc(application.getProviderId()).stream().anyMatch(s -> s.isActive());
        boolean slots = application.getProviderId() != null
                && !slotRepository.findByProviderIdAndStatusAndStartAtAfterOrderByStartAtAsc(application.getProviderId(), SlotStatus.OPEN, LocalDateTime.now()).isEmpty();
        boolean profile = application.getProfileConfirmedAt() != null;

        List<String> missing = new ArrayList<>();
        if (!agreement) missing.add("Sign the agreement");
        if (bank == null) missing.add("Add bank and tax details");
        else if (!bankPassed) missing.add("Bank test deposit must pass");
        if (!training) missing.add("Pass the training");
        if (!services) missing.add("Add at least one service");
        if (!slots) missing.add("Add at least one available slot");
        if (!profile) missing.add("Confirm your profile");
        if (application.isRecheckRequired()) missing.add("Operations must re-check this approval");
        if (ProviderLifecycleService.licenceExpired(application)) missing.add("The licence on file has expired");

        return new SetupStatusResponse(agreement, bank != null, bank == null ? null : bank.getVerificationStatus(), bank != null, training,
                services, slots, profile, application.isRecheckRequired(), missing.isEmpty(), missing);
    }

    public AgreementResponse agreement() {
        ProviderApplication application = requireInSetupOrLater();
        BigDecimal fee = feeFor(application.getProfessionalType());
        return new AgreementResponse(AgreementCatalog.AGREEMENT_VERSION, agreementCatalog.agreementText(fee), fee, AgreementCatalog.IS_DRAFT,
                signingEnabled, application.getAgreementTypedName(), application.getAgreementSignedAt());
    }

    @Transactional
    public AgreementResponse signAgreement(String typedName) {
        ProviderApplication application = requireVerified();
        if (!signingEnabled) {
            throw new ProviderOnboardingException(409, "AGREEMENT_NOT_READY", "The final agreement isn't ready yet. We'll let you know when you can sign.");
        }
        String legalName = answerStore.read(application, ApplicationFormCatalog.KEY_LEGAL_NAME);
        if (legalName == null || !legalName.trim().equalsIgnoreCase(typedName.trim())) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please type your full legal name exactly as in your application.",
                    Map.of("fields", Map.of("typedName", "This must match your legal name.")));
        }
        if (application.getAgreementSignedAt() == null) {
            application.setAgreementVersion(AgreementCatalog.AGREEMENT_VERSION);
            application.setAgreementTypedName(typedName.trim());
            application.setAgreementSignedAt(LocalDateTime.now());
            applicationRepository.save(application);
            auditService.record("PROVIDER_AGREEMENT_SIGNED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), null,
                    Map.of("version", AgreementCatalog.AGREEMENT_VERSION));
        }
        return agreement();
    }

    public BankDetailsResponse bank() {
        return bankRepository.findById(requireInSetupOrLater().getId()).map(ApplicationSetupService::toBankResponse).orElse(null);
    }

    /** Setup only. A live professional's bank change goes through ProfileChangeService, so the old details stay until approved. */
    @Transactional
    public BankDetailsResponse saveBank(BankDetailsRequest req) {
        ProviderApplication application = requireVerified();
        ProviderBankDetails saved = bankRepository.save(applyBank(application.getId(), req));
        auditService.record("PROVIDER_BANK_DETAILS_SAVED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), null);
        return toBankResponse(saved);
    }

    /** Checks the details and changes nothing, so a change request can be validated without touching the record on file. */
    public void validateBank(BankDetailsRequest req) {
        if (!req.accountNumber().equals(req.accountNumberConfirm())) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "The account numbers don't match.",
                    Map.of("fields", Map.of("accountNumberConfirm", "The account numbers don't match.")));
        }
        if (req.gstRegistered() && (req.gstin() == null || req.gstin().isBlank())) {
            throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Please enter your GSTIN.",
                    Map.of("fields", Map.of("gstin", "Please enter your GSTIN.")));
        }
    }

    /** Applies validated details to the record on file. The account number is encrypted and needs a fresh test deposit. */
    public ProviderBankDetails applyBank(Long applicationId, BankDetailsRequest req) {
        validateBank(req);
        ProviderBankDetails bank = bankRepository.findById(applicationId).orElseGet(ProviderBankDetails::new);
        bank.setApplicationId(applicationId);
        bank.setAccountHolderName(req.accountHolderName().trim());
        bank.setAccountNumberEncrypted(encryptor.encrypt(req.accountNumber()));
        bank.setAccountLast4(req.accountNumber().substring(req.accountNumber().length() - 4));
        bank.setIfsc(req.ifsc().toUpperCase(Locale.ROOT));
        bank.setAccountType(req.accountType());
        bank.setGstRegistered(req.gstRegistered());
        bank.setGstin(req.gstRegistered() ? req.gstin() : null);
        bank.setVerificationStatus(BankVerificationStatus.PENDING);
        bank.setVerifiedAt(null);
        bank.setVerifiedByUserId(null);
        return bank;
    }

    public TrainingResponse training() {
        return new TrainingResponse(trainingCatalog.questions(), AgreementCatalog.IS_DRAFT, requireInSetupOrLater().getTrainingPassedAt());
    }

    @Transactional
    public TrainingResult submitTraining(Map<String, Integer> answers) {
        ProviderApplication application = requireVerified();
        boolean passed = trainingCatalog.passes(answers);
        if (passed && application.getTrainingPassedAt() == null) {
            application.setTrainingPassedAt(LocalDateTime.now());
            applicationRepository.save(application);
            auditService.record("PROVIDER_TRAINING_PASSED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), null);
        }
        return new TrainingResult(passed, trainingCatalog.score(answers), trainingCatalog.questions().size());
    }

    @Transactional
    public SetupStatusResponse confirmProfile() {
        ProviderApplication application = requireVerified();
        application.setProfileConfirmedAt(LocalDateTime.now());
        applicationRepository.save(application);
        return status(application);
    }

    public BigDecimal feeFor(Role profession) {
        return payoutRateRepository.findByProfessionalType(profession)
                .map(rate -> rate.getAmount())
                .orElseThrow(() -> new ProviderOnboardingException(409, "FEE_NOT_SET", "The fee for this profession isn't set yet."));
    }

    private ProviderApplication requireVerified() {
        ProviderApplication application = applicationService.requireCurrent();
        if (application.getStatus() != ApplicationStatus.VERIFIED) {
            throw new ProviderOnboardingException(409, "SETUP_NOT_OPEN", "Setup is only open after approval and before going live.");
        }
        return application;
    }

    private ProviderApplication requireInSetupOrLater() {
        ProviderApplication application = applicationService.requireCurrent();
        if (!application.getStatus().isApproved()) {
            throw new ProviderOnboardingException(409, "SETUP_NOT_OPEN", "Setup opens once your application is approved.");
        }
        return application;
    }

    private static BankDetailsResponse toBankResponse(ProviderBankDetails bank) {
        return new BankDetailsResponse(bank.getAccountHolderName(), bank.getAccountLast4(), bank.getIfsc(), bank.getAccountType(),
                bank.isGstRegistered(), bank.getGstin(), bank.getVerificationStatus());
    }
}
