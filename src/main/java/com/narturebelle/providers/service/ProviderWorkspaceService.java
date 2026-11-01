package com.narturebelle.providers.service;

import com.narturebelle.model.SessionFeedback;
import com.narturebelle.repository.SessionFeedbackRepository;
import org.springframework.data.domain.PageRequest;
import com.narturebelle.providers.dto.WorkspaceDtos.EarningItem;
import com.narturebelle.providers.dto.WorkspaceDtos.EarningsResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.FeedbackItem;
import com.narturebelle.providers.dto.WorkspaceDtos.FeedbackResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.ProductActivityItem;
import com.narturebelle.providers.dto.WorkspaceDtos.ProfileResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.PublicCredentialsResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.ServiceRequest;
import com.narturebelle.providers.dto.WorkspaceDtos.ServiceResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.SlotResponse;
import com.narturebelle.providers.dto.WorkspaceDtos.SlotWindow;
import com.narturebelle.providers.exception.ProviderOnboardingException;
import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.model.Payout;
import com.narturebelle.model.Product;
import com.narturebelle.model.Provider;
import com.narturebelle.model.ProviderSlot;
import com.narturebelle.model.ServiceOffering;
import com.narturebelle.providers.repository.ProviderApplicationRepository;
import com.narturebelle.repository.PayoutRepository;
import com.narturebelle.repository.ProductRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.ProviderSlotRepository;
import com.narturebelle.repository.ServiceOfferingRepository;
import com.narturebelle.repository.SessionProductRecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** The dashboard's own data. Sessions themselves come from NB-Ecom-API's existing /provider/bookings. */
@Service
public class ProviderWorkspaceService {

    private final ApplicationService applicationService;
    private final ApplicationSetupService setupService;
    private final ApplicationAnswerStore answerStore;
    private final ProviderApplicationRepository applicationRepository;
    private final ProviderRepository providerRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final ProviderSlotRepository slotRepository;
    private final PayoutRepository payoutRepository;
    private final SessionProductRecommendationRepository recommendationRepository;
    private final ProductRepository productRepository;
    private final SessionFeedbackRepository feedbackRepository;

    public ProviderWorkspaceService(ApplicationService applicationService, ApplicationSetupService setupService, ApplicationAnswerStore answerStore,
                                    ProviderApplicationRepository applicationRepository, ProviderRepository providerRepository,
                                    ServiceOfferingRepository serviceRepository, ProviderSlotRepository slotRepository,
                                    PayoutRepository payoutRepository, SessionProductRecommendationRepository recommendationRepository,
                                    ProductRepository productRepository, SessionFeedbackRepository feedbackRepository) {
        this.applicationService = applicationService;
        this.setupService = setupService;
        this.answerStore = answerStore;
        this.applicationRepository = applicationRepository;
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
        this.slotRepository = slotRepository;
        this.payoutRepository = payoutRepository;
        this.recommendationRepository = recommendationRepository;
        this.productRepository = productRepository;
        this.feedbackRepository = feedbackRepository;
    }

    // ---- services ----

    public List<ServiceResponse> services() {
        return serviceRepository.findByProviderIdOrderByCreatedAtDesc(requireApproved().getProviderId()).stream().map(ServiceResponse::from).toList();
    }

    public ServiceResponse addService(ServiceRequest req) {
        ProviderApplication application = requireWorkable();
        ServiceOffering offering = new ServiceOffering();
        offering.setProviderId(application.getProviderId());
        offering.setName(req.name().trim());
        offering.setDescription(req.description());
        offering.setModality(req.modality());
        offering.setDurationMinutes(req.durationMinutes());
        offering.setActive(true);
        return ServiceResponse.from(serviceRepository.save(offering));
    }

    public ServiceResponse setServiceActive(Long serviceId, boolean active) {
        ProviderApplication application = requireWorkable();
        ServiceOffering offering = serviceRepository.findById(serviceId)
                .filter(found -> found.getProviderId().equals(application.getProviderId()))
                .orElseThrow(() -> new ProviderOnboardingException(404, "SERVICE_NOT_FOUND", "Service not found."));
        offering.setActive(active);
        return ServiceResponse.from(serviceRepository.save(offering));
    }

    // ---- availability ----

    public List<SlotResponse> slots() {
        return openSlots(requireApproved().getProviderId()).stream().map(SlotResponse::from).toList();
    }

    @Transactional
    public List<SlotResponse> addSlots(List<SlotWindow> windows) {
        ProviderApplication application = requireWorkable();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < windows.size(); i++) {
            SlotWindow window = windows.get(i);
            if (!window.startAt().isAfter(now) || !window.endAt().isAfter(window.startAt())) {
                throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "A slot must be in the future and end after it starts.");
            }
            if (window.startAt().isAfter(now.plusMonths(6))) {
                throw new ProviderOnboardingException(400, "VALIDATION_FAILED", "Slots can be added up to 6 months ahead.");
            }
            boolean clashes = slotRepository.overlaps(application.getProviderId(), window.startAt(), window.endAt());
            for (int j = 0; !clashes && j < i; j++) {
                clashes = windows.get(j).startAt().isBefore(window.endAt()) && window.startAt().isBefore(windows.get(j).endAt());
            }
            if (clashes) {
                throw new ProviderOnboardingException(409, "SLOT_OVERLAP", "One of these slots overlaps another of your slots.");
            }
        }
        return windows.stream().map(window -> {
            ProviderSlot slot = new ProviderSlot();
            slot.setProviderId(application.getProviderId());
            slot.setStartAt(window.startAt());
            slot.setEndAt(window.endAt());
            slot.setStatus(SlotStatus.OPEN);
            return SlotResponse.from(slotRepository.save(slot));
        }).toList();
    }

    /** Only an open slot can be removed; a booked session has to be cancelled through the booking. */
    public void removeSlot(Long slotId) {
        ProviderApplication application = requireWorkable();
        ProviderSlot slot = slotRepository.findById(slotId)
                .filter(found -> found.getProviderId().equals(application.getProviderId()))
                .orElseThrow(() -> new ProviderOnboardingException(404, "SLOT_NOT_FOUND", "Slot not found."));
        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new ProviderOnboardingException(409, "SLOT_NOT_OPEN", "This slot is already booked or removed.");
        }
        slot.setStatus(SlotStatus.CANCELLED);
        slotRepository.save(slot);
    }

    // ---- earnings, product activity, profile ----

    /** Earned from NB-Ecom-API's payout rows. No bank transfer exists yet, so nothing is reported as paid out. */
    public EarningsResponse earnings() {
        ProviderApplication application = requireApproved();
        List<Payout> payouts = payoutRepository.findByProviderIdOrderByCreatedAtDesc(application.getProviderId());
        BigDecimal total = payouts.stream().map(Payout::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<EarningItem> items = payouts.stream()
                .map(payout -> new EarningItem(payout.getBookingId(), payout.getAmount(), payout.getStatus().name(), payout.getCreatedAt())).toList();
        return new EarningsResponse(setupService.feeFor(application.getProfessionalType()), total, BigDecimal.ZERO, payouts.size(),
                AgreementCatalog.IS_DRAFT, items);
    }

    /** The latest 50, without anything identifying the mother. */
    public FeedbackResponse feedback() {
        List<SessionFeedback> feedback = feedbackRepository.findForProvider(requireApproved().getProviderId(), PageRequest.of(0, 50));
        Double average = feedback.isEmpty() ? null
                : feedback.stream().mapToInt(SessionFeedback::getRating).average().orElse(0);
        return new FeedbackResponse(feedback.size(), average, feedback.stream()
                .map(item -> new FeedbackItem(item.getRating(), item.getComment(), item.getCreatedAt().toLocalDate()))
                .toList());
    }

    public List<ProductActivityItem> productActivity() {
        ProviderApplication application = requireApproved();
        return recommendationRepository.findTop20ByProviderIdOrderByCreatedAtDesc(application.getProviderId()).stream().map(item -> {
            String name = productRepository.findById(item.getProductId()).map(Product::getName).orElse("Product");
            return new ProductActivityItem(name, item.getPriority().name(), item.getNote(), item.getCreatedAt());
        }).toList();
    }

    public ProfileResponse profile() {
        ProviderApplication application = requireApproved();
        Provider provider = requireProvider(application);
        List<String> stages = ApplicationAnswerStore.splitMulti(application.getClearedStages() == null ? null : application.getClearedStages().replace(",", "\n"));
        return new ProfileResponse(provider.getId(), application.getProfessionalType(), provider.getStatus(),
                answerStore.read(application, ApplicationFormCatalog.KEY_LEGAL_NAME), provider.getDisplayName(), provider.getBio(), provider.getCity(),
                List.copyOf(provider.getLanguages()), answerStore.read(application, ApplicationFormCatalog.KEY_COUNCIL), application.getRegistrationNumber(),
                application.getLicenceExpiresOn(), application.getConfirmedExperienceYears(), stages, application.isCanPrescribe(), setupService.bank());
    }

    /** Public: only for live professionals, so a hidden application can't be probed by provider id. */
    public PublicCredentialsResponse publicCredentials(Long providerId) {
        ProviderApplication application = applicationRepository.findByProviderId(providerId)
                .filter(found -> found.getStatus() == ApplicationStatus.ACTIVE)
                .orElseThrow(() -> new ProviderOnboardingException(404, "PROVIDER_NOT_FOUND", "Provider not found."));
        return new PublicCredentialsResponse(application.getProfessionalType(), answerStore.read(application, ApplicationFormCatalog.KEY_COUNCIL),
                application.getRegistrationNumber(), application.getConfirmedExperienceYears());
    }

    // ---- helpers ----

    private List<ProviderSlot> openSlots(Long providerId) {
        return slotRepository.findByProviderIdAndStatusAndStartAtAfterOrderByStartAtAsc(providerId, SlotStatus.OPEN, LocalDateTime.now());
    }

    private ProviderApplication requireApproved() {
        ProviderApplication application = applicationService.requireCurrent();
        if (!application.getStatus().isApproved() || application.getProviderId() == null) {
            throw new ProviderOnboardingException(403, "NOT_APPROVED", "Your dashboard opens once your application is approved.");
        }
        return application;
    }

    private ProviderApplication requireWorkable() {
        ProviderApplication application = requireApproved();
        if (application.getStatus() != ApplicationStatus.VERIFIED && application.getStatus() != ApplicationStatus.ACTIVE) {
            throw new ProviderOnboardingException(409, "WORKSPACE_LOCKED", "This can't be changed while your profile is paused or suspended.", Map.of());
        }
        return application;
    }

    private Provider requireProvider(ProviderApplication application) {
        return providerRepository.findById(application.getProviderId())
                .orElseThrow(() -> new IllegalStateException("Application references a missing provider: " + application.getProviderId()));
    }
}
