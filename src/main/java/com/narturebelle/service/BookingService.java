package com.narturebelle.service;

import com.narturebelle.dto.CreateBookingRequest;
import com.narturebelle.dto.SubmitFeedbackRequest;
import com.narturebelle.exception.BookingNotAllowedException;
import com.narturebelle.exception.FeedbackNotAllowedException;
import com.narturebelle.exception.NoEntitlementAvailableException;
import com.narturebelle.exception.ProviderNotBookableException;
import com.narturebelle.exception.ProviderNotFoundException;
import com.narturebelle.exception.ServiceOfferingNotFoundException;
import com.narturebelle.exception.SlotNotAvailableException;
import com.narturebelle.model.Booking;
import com.narturebelle.model.BookingNoteAmendment;
import com.narturebelle.model.BookingStatus;
import com.narturebelle.model.Entitlement;
import com.narturebelle.model.NoShowParty;
import com.narturebelle.model.Provider;
import com.narturebelle.model.ProviderSlot;
import com.narturebelle.model.ProviderStatus;
import com.narturebelle.model.ServiceOffering;
import com.narturebelle.model.SessionFeedback;
import com.narturebelle.repository.BookingNoteAmendmentRepository;
import com.narturebelle.repository.BookingRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.ProviderSlotRepository;
import com.narturebelle.repository.ServiceOfferingRepository;
import com.narturebelle.repository.SessionFeedbackRepository;
import com.narturebelle.security.AuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ProviderRepository providerRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ProviderSlotRepository providerSlotRepository;
    private final BookingNoteAmendmentRepository bookingNoteAmendmentRepository;
    private final SessionFeedbackRepository sessionFeedbackRepository;
    private final EntitlementService entitlementService;
    private final PayoutService payoutService;
    private final AuditService auditService;

    public BookingService(BookingRepository bookingRepository, ProviderRepository providerRepository,
                           ServiceOfferingRepository serviceOfferingRepository, ProviderSlotRepository providerSlotRepository,
                           BookingNoteAmendmentRepository bookingNoteAmendmentRepository,
                           SessionFeedbackRepository sessionFeedbackRepository, EntitlementService entitlementService,
                           PayoutService payoutService, AuditService auditService) {
        this.bookingRepository = bookingRepository;
        this.providerRepository = providerRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.providerSlotRepository = providerSlotRepository;
        this.bookingNoteAmendmentRepository = bookingNoteAmendmentRepository;
        this.sessionFeedbackRepository = sessionFeedbackRepository;
        this.entitlementService = entitlementService;
        this.payoutService = payoutService;
        this.auditService = auditService;
    }

    @Transactional
    public Booking createBooking(Long userId, CreateBookingRequest req) {
        Provider provider = providerRepository.findById(req.getProviderId())
                .orElseThrow(() -> new ProviderNotFoundException("No provider with id " + req.getProviderId()));
        if (provider.getStatus() != ProviderStatus.VERIFIED && provider.getStatus() != ProviderStatus.ACTIVE) {
            throw new ProviderNotBookableException("Provider is not currently bookable (status " + provider.getStatus() + ").");
        }

        ServiceOffering offering = serviceOfferingRepository.findByIdAndProviderId(req.getServiceOfferingId(), provider.getId())
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> new ServiceOfferingNotFoundException(
                        "No active service offering " + req.getServiceOfferingId() + " for provider " + provider.getId()));

        if (!entitlementService.hasAvailableSession(userId, provider.getProfessionalType())) {
            throw new NoEntitlementAvailableException(
                    "No available " + provider.getProfessionalType() + " session for user " + userId);
        }

        ProviderSlot slot = providerSlotRepository.findById(req.getSlotId())
                .orElseThrow(() -> new SlotNotAvailableException("No such slot: " + req.getSlotId()));
        if (providerSlotRepository.claimSlot(slot.getId()) == 0) {
            throw new SlotNotAvailableException("Slot " + slot.getId() + " is no longer available.");
        }

        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setProviderId(provider.getId());
        booking.setServiceOfferingId(offering.getId());
        booking.setSlotId(slot.getId());
        booking.setProfessionalType(provider.getProfessionalType());
        booking.setStatus(BookingStatus.BOOKED);
        booking.setScheduledStart(slot.getStartAt());
        booking.setScheduledEnd(slot.getEndAt());

        Booking saved;
        try {
            saved = bookingRepository.save(booking);
        } catch (DataIntegrityViolationException race) {
            throw new SlotNotAvailableException("Slot " + slot.getId() + " is no longer available.");
        }

        auditService.record("BOOKING_CREATED", "Booking", String.valueOf(saved.getId()), null,
                Map.of("providerId", provider.getId(), "slotId", slot.getId()));

        return saved;
    }

    public List<Booking> listForUser(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Booking> listForProvider(Long providerId) {
        return bookingRepository.findByProviderIdOrderByCreatedAtDesc(providerId);
    }

    public Optional<Booking> find(Long id) {
        return bookingRepository.findById(id);
    }

    @Transactional
    public Booking cancelBooking(Booking booking, Long actorUserId, String reason) {
        requireStatus(booking, "cancel", BookingStatus.BOOKED);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancelledByUserId(actorUserId);
        booking.setCancelReason(reason);
        Booking saved = bookingRepository.save(booking);
        reopenSlot(booking.getSlotId());

        auditService.record("BOOKING_CANCELLED", "Booking", String.valueOf(saved.getId()), reason, null);
        return saved;
    }

    @Transactional
    public Booking rescheduleBooking(Booking booking, Long actorUserId, Long newSlotId, String reason) {
        requireStatus(booking, "reschedule", BookingStatus.BOOKED);

        ProviderSlot newSlot = providerSlotRepository.findById(newSlotId)
                .orElseThrow(() -> new SlotNotAvailableException("No such slot: " + newSlotId));
        if (providerSlotRepository.claimSlot(newSlotId) == 0) {
            throw new SlotNotAvailableException("Slot " + newSlotId + " is no longer available.");
        }

        booking.setStatus(BookingStatus.RESCHEDULED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancelledByUserId(actorUserId);
        booking.setCancelReason(reason);
        bookingRepository.save(booking);
        reopenSlot(booking.getSlotId());

        Booking newBooking = new Booking();
        newBooking.setUserId(booking.getUserId());
        newBooking.setProviderId(booking.getProviderId());
        newBooking.setServiceOfferingId(booking.getServiceOfferingId());
        newBooking.setSlotId(newSlot.getId());
        newBooking.setProfessionalType(booking.getProfessionalType());
        newBooking.setStatus(BookingStatus.BOOKED);
        newBooking.setScheduledStart(newSlot.getStartAt());
        newBooking.setScheduledEnd(newSlot.getEndAt());
        newBooking.setRescheduledFromBookingId(booking.getId());

        Booking saved;
        try {
            saved = bookingRepository.save(newBooking);
        } catch (DataIntegrityViolationException race) {
            throw new SlotNotAvailableException("Slot " + newSlotId + " is no longer available.");
        }

        auditService.record("BOOKING_RESCHEDULED", "Booking", String.valueOf(booking.getId()), reason,
                Map.of("newBookingId", saved.getId(), "newSlotId", newSlot.getId()));
        return saved;
    }

    public Booking startSession(Booking booking) {
        requireStatus(booking, "start", BookingStatus.BOOKED);
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setStartedAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);
        auditService.record("SESSION_STARTED", "Booking", String.valueOf(saved.getId()), null, null);
        return saved;
    }

    @Transactional
    public Booking completeSession(Booking booking, String note) {
        requireStatus(booking, "complete", BookingStatus.BOOKED, BookingStatus.IN_PROGRESS);

        Entitlement consumed = entitlementService.consumeOneSession(booking.getUserId(), booking.getProfessionalType());

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now());
        booking.setProviderNote(note);
        booking.setNoteLockedAt(LocalDateTime.now());
        booking.setConsumedEntitlementId(consumed.getId());
        Booking saved = bookingRepository.save(booking);

        payoutService.createPayoutForBooking(saved);
        auditService.record("SESSION_COMPLETED", "Booking", String.valueOf(saved.getId()), null, null);
        return saved;
    }

    /** Deliberately: no entitlement consumption, no payout — per the deferred no-show policy default. */
    @Transactional
    public Booking recordNoShow(Booking booking, Long actorUserId, NoShowParty party, String reason) {
        requireStatus(booking, "record a no-show for", BookingStatus.BOOKED, BookingStatus.IN_PROGRESS);
        booking.setStatus(party == NoShowParty.PROVIDER ? BookingStatus.NO_SHOW_PROVIDER : BookingStatus.NO_SHOW_USER);
        booking.setNoShowAt(LocalDateTime.now());
        booking.setNoShowRecordedByUserId(actorUserId);
        booking.setNoShowReason(reason);
        Booking saved = bookingRepository.save(booking);

        auditService.record("SESSION_NO_SHOW", "Booking", String.valueOf(saved.getId()), reason,
                Map.of("party", party.name()));
        return saved;
    }

    @Transactional
    public Booking amendNote(Booking booking, Long actorUserId, String newNote, String reason) {
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BookingNotAllowedException(
                    "Notes can only be amended after completion; current status is " + booking.getStatus());
        }
        String previousNote = booking.getProviderNote();
        booking.setProviderNote(newNote);
        Booking saved = bookingRepository.save(booking);

        BookingNoteAmendment amendment = new BookingNoteAmendment();
        amendment.setBookingId(booking.getId());
        amendment.setPreviousNote(previousNote);
        amendment.setNewNote(newNote);
        amendment.setReason(reason);
        amendment.setAmendedByUserId(actorUserId);
        bookingNoteAmendmentRepository.save(amendment);

        auditService.record("BOOKING_NOTE_AMENDED", "Booking", String.valueOf(saved.getId()), reason, null);
        return saved;
    }

    public SessionFeedback submitFeedback(Booking booking, Long userId, SubmitFeedbackRequest req) {
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new FeedbackNotAllowedException("Feedback can only be submitted after the session is completed.");
        }
        if (sessionFeedbackRepository.existsByBookingId(booking.getId())) {
            throw new FeedbackNotAllowedException("Feedback was already submitted for this booking.");
        }
        SessionFeedback feedback = new SessionFeedback();
        feedback.setBookingId(booking.getId());
        feedback.setUserId(userId);
        feedback.setRating(req.getRating());
        feedback.setComment(req.getComment());
        SessionFeedback saved = sessionFeedbackRepository.save(feedback);

        auditService.record("SESSION_FEEDBACK_SUBMITTED", "Booking", String.valueOf(booking.getId()), null, null);
        return saved;
    }

    private void reopenSlot(Long slotId) {
        providerSlotRepository.findById(slotId).ifPresent(slot -> {
            slot.setStatus(com.narturebelle.model.SlotStatus.OPEN);
            providerSlotRepository.save(slot);
        });
    }

    private void requireStatus(Booking booking, String action, BookingStatus... allowed) {
        for (BookingStatus status : allowed) {
            if (booking.getStatus() == status) return;
        }
        throw new BookingNotAllowedException(
                "Cannot " + action + " a booking with status " + booking.getStatus());
    }
}
