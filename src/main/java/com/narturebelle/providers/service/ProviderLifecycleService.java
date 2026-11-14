package com.narturebelle.providers.service;

import com.narturebelle.providers.exception.ProviderOnboardingException;
import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.model.BookingStatus;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.model.ProviderStatus;
import com.narturebelle.model.Role;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.model.Booking;
import com.narturebelle.model.Provider;
import com.narturebelle.model.User;
import com.narturebelle.providers.repository.ProviderApplicationRepository;
import com.narturebelle.repository.BookingRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.ProviderSlotRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps public.providers in step with the application. NB-Ecom-API lists and books only VERIFIED and ACTIVE
 * providers, so an approved professional's row stays UNDER_REVIEW (hidden and unbookable) until go-live.
 */
@Service
public class ProviderLifecycleService {

    private static final String MOTHER_CANCEL_REASON = "Your care team can help you rebook.";

    private final ProviderApplicationRepository applicationRepository;
    private final ProviderRepository providerRepository;
    private final ProviderSlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AuthorizationService authorizationService;
    private final ApplicationNotifier notifier;

    public ProviderLifecycleService(ProviderApplicationRepository applicationRepository, ProviderRepository providerRepository,
                                    ProviderSlotRepository slotRepository, BookingRepository bookingRepository,
                                    UserRepository userRepository, AuditService auditService,
                                    AuthorizationService authorizationService, ApplicationNotifier notifier) {
        this.applicationRepository = applicationRepository;
        this.providerRepository = providerRepository;
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.authorizationService = authorizationService;
        this.notifier = notifier;
    }

    /** Called when the Medical Reviewer approves: the row exists from now on, but mothers can't see it. */
    public Provider createHiddenProvider(ProviderApplication application, String displayName, String bio, String city, List<String> languages) {
        Provider provider = providerRepository.findByUserId(application.getUserId()).orElseGet(Provider::new);
        provider.setUserId(application.getUserId());
        provider.setProfessionalType(application.getProfessionalType());
        provider.setDisplayName(displayName);
        provider.setBio(bio);
        provider.setCity(city);
        provider.setLanguages(new ArrayList<>(languages));
        provider.setStatus(ProviderStatus.UNDER_REVIEW);
        return providerRepository.save(provider);
    }

    /** Operations sets the professional live: visible, bookable, and holding their professional role from the next request. */
    @Transactional
    public void goLive(ProviderApplication application) {
        requireValidLicence(application);
        Provider provider = requireProvider(application);
        provider.setStatus(ProviderStatus.ACTIVE);
        providerRepository.save(provider);

        User user = userRepository.findById(application.getUserId())
                .orElseThrow(() -> new IllegalStateException("Application references a missing user: " + application.getUserId()));
        // Owner decision: a live professional is a professional only. The application form already refuses accounts
        // with mother data, so nothing is lost. Every API check looks for a specific role; none relies on MOTHER.
        user.getRoles().remove(Role.MOTHER);
        user.getRoles().add(application.getProfessionalType());
        userRepository.save(user);

        application.setStatus(ApplicationStatus.ACTIVE);
        application.setActivatedAt(LocalDateTime.now());
        applicationRepository.save(application);

        auditService.record("PROVIDER_WENT_LIVE", "PROVIDER_APPLICATION", String.valueOf(application.getId()), null,
                Map.of("providerId", provider.getId()));
        auditService.record("ROLE_GRANTED", "User", String.valueOf(user.getId()), "Provider went live",
                Map.of("role", application.getProfessionalType().name()));
        notifier.notifyUser("PROVIDER_LIVE", application.getUserId(), "You're live on NartureBelle",
                "Your profile is now visible and mothers can book sessions with you. Your dashboard has your sessions and availability.");
    }

    /** Pause, suspend or restore a professional. NB-Ecom-API stops listing and booking them as soon as the status changes. */
    @Transactional
    public void changeStatus(ProviderApplication application, ApplicationStatus target, String reason) {
        Set<ApplicationStatus> allowedFrom = target == ApplicationStatus.ACTIVE
                ? Set.of(ApplicationStatus.PAUSED, ApplicationStatus.SUSPENDED)
                : Set.of(ApplicationStatus.ACTIVE, ApplicationStatus.PAUSED, ApplicationStatus.SUSPENDED);
        if ((target != ApplicationStatus.ACTIVE && target != ApplicationStatus.PAUSED && target != ApplicationStatus.SUSPENDED)
                || !allowedFrom.contains(application.getStatus()) || application.getStatus() == target) {
            throw new ProviderOnboardingException(409, "INVALID_STATUS_CHANGE",
                    "Can't change from " + application.getStatus() + " to " + target + ".");
        }
        if (target == ApplicationStatus.ACTIVE) {
            requireValidLicence(application);
        }
        ApplicationStatus from = application.getStatus();
        setStatus(application, target);
        if (target == ApplicationStatus.SUSPENDED) {
            cancelUpcomingBookings(application, "Provider suspended");
        }
        auditService.record("PROVIDER_STATUS_CHANGED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), reason,
                Map.of("from", from.name(), "to", target.name()));
    }

    /** The licence expired: hidden and unbookable, and no one can run a session without a valid licence. */
    @Transactional
    public void pauseForLapsedLicence(ProviderApplication application) {
        application.setLicenceLapsed(true);
        setStatus(application, ApplicationStatus.PAUSED);
        cancelUpcomingBookings(application, "Provider licence lapsed");
        auditService.record("PROVIDER_LICENCE_LAPSED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), "Licence expired");
        notifier.notifyUser(application.getUserId(), "Your NartureBelle profile is paused",
                "Your licence on file has expired, so your profile is hidden and upcoming sessions were cancelled. Upload your renewed licence from your dashboard to return.");
    }

    /** A licence change was approved. A lapse only clears once the date on file is valid again. */
    public void restoreAfterLicenceRenewal(ProviderApplication application) {
        if (application.getLicenceExpiresOn() == null || licenceExpired(application)) {
            applicationRepository.save(application);
            return;
        }
        boolean wasLapsed = application.isLicenceLapsed();
        application.setLicenceLapsed(false);
        if (wasLapsed && application.getStatus() == ApplicationStatus.PAUSED) {
            setStatus(application, ApplicationStatus.ACTIVE);
        } else {
            applicationRepository.save(application);
        }
    }

    public static boolean licenceExpired(ProviderApplication application) {
        return application.getLicenceExpiresOn() != null && application.getLicenceExpiresOn().isBefore(LocalDate.now());
    }

    private void requireValidLicence(ProviderApplication application) {
        if (application.isLicenceLapsed() || licenceExpired(application)) {
            throw new ProviderOnboardingException(409, "LICENCE_LAPSED", "A new licence document must be approved first.");
        }
    }

    private void setStatus(ProviderApplication application, ApplicationStatus status) {
        Provider provider = requireProvider(application);
        provider.setStatus(ProviderStatus.valueOf(status.name()));
        providerRepository.save(provider);
        application.setStatus(status);
        applicationRepository.save(application);
    }

    /** Sessions are only deducted on completion, so a cancelled one costs the mother nothing. She isn't told the reason. */
    private void cancelUpcomingBookings(ProviderApplication application, String auditReason) {
        LocalDateTime now = LocalDateTime.now();
        Long actor = authorizationService.currentPrincipalOrEmpty().map(principal -> principal.userId()).orElse(null);
        for (Booking booking : bookingRepository.findByProviderIdAndStatusAndScheduledStartAfter(application.getProviderId(), BookingStatus.BOOKED, now)) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(now);
            booking.setCancelledByUserId(actor);
            booking.setCancelReason(MOTHER_CANCEL_REASON);
            bookingRepository.save(booking);
            auditService.record("BOOKING_CANCELLED", "Booking", String.valueOf(booking.getId()), auditReason);
            slotRepository.findById(booking.getSlotId()).ifPresent(slot -> {
                slot.setStatus(SlotStatus.CANCELLED);
                slotRepository.save(slot);
            });
            notifier.notifyUser(booking.getUserId(), "Your NartureBelle session was cancelled",
                    "Your upcoming care session has been cancelled, and no session was deducted from your plan. " + MOTHER_CANCEL_REASON);
        }
    }

    private Provider requireProvider(ProviderApplication application) {
        return providerRepository.findById(application.getProviderId())
                .orElseThrow(() -> new IllegalStateException("Application references a missing provider: " + application.getProviderId()));
    }
}
