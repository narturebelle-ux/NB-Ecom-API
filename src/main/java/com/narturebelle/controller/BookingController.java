package com.narturebelle.controller;

import com.narturebelle.dto.AmendNoteRequest;
import com.narturebelle.dto.BookingResponse;
import com.narturebelle.dto.CancelBookingRequest;
import com.narturebelle.dto.CompleteSessionRequest;
import com.narturebelle.dto.CreateBookingRequest;
import com.narturebelle.dto.NoShowRequest;
import com.narturebelle.dto.RescheduleBookingRequest;
import com.narturebelle.dto.SessionFeedbackResponse;
import com.narturebelle.dto.SubmitFeedbackRequest;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.Booking;
import com.narturebelle.model.NoShowParty;
import com.narturebelle.model.Provider;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireVerified;
import com.narturebelle.service.BookingService;
import com.narturebelle.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final ProviderService providerService;
    private final AuthorizationService authorizationService;

    public BookingController(BookingService bookingService, ProviderService providerService,
                              AuthorizationService authorizationService) {
        this.bookingService = bookingService;
        this.providerService = providerService;
        this.authorizationService = authorizationService;
    }

    @RequireVerified
    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        Booking booking = bookingService.createBooking(userId, req);
        return ResponseEntity.ok(BookingResponse.from(booking));
    }

    @RequireVerified
    @GetMapping
    public ResponseEntity<List<BookingResponse>> list() {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        Long userId = requireUserId(principal);
        Provider callerProvider = providerService.findByUserId(userId).orElse(null);

        List<Booking> bookings = callerProvider != null
                ? bookingService.listForProvider(callerProvider.getId())
                : bookingService.listForUser(userId);
        return ResponseEntity.ok(bookings.stream().map(BookingResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getOne(@PathVariable Long id) {
        Booking booking = bookingService.find(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireBookingAccess(booking.getUserId(), booking.getProviderId());
        return ResponseEntity.ok(BookingResponse.from(booking));
    }

    @RequireVerified
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancel(@PathVariable Long id, @Valid @RequestBody CancelBookingRequest req) {
        Booking booking = bookingService.find(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        authorizationService.requireBookingAccess(booking.getUserId(), booking.getProviderId());
        Booking saved = bookingService.cancelBooking(booking, principal.userId(), req.getReason());
        return ResponseEntity.ok(BookingResponse.from(saved));
    }

    @RequireVerified
    @PostMapping("/{id}/reschedule")
    public ResponseEntity<BookingResponse> reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleBookingRequest req) {
        Booking booking = bookingService.find(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        authorizationService.requireBookingAccess(booking.getUserId(), booking.getProviderId());
        Booking saved = bookingService.rescheduleBooking(booking, principal.userId(), req.getNewSlotId(), req.getReason());
        return ResponseEntity.ok(BookingResponse.from(saved));
    }

    @RequireVerified
    @PostMapping("/{id}/start")
    public ResponseEntity<BookingResponse> start(@PathVariable Long id) {
        Booking booking = bookingService.find(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        requireProviderOrAdmin(booking);
        Booking saved = bookingService.startSession(booking);
        return ResponseEntity.ok(BookingResponse.from(saved));
    }

    @RequireVerified
    @PostMapping("/{id}/complete")
    public ResponseEntity<BookingResponse> complete(@PathVariable Long id, @Valid @RequestBody CompleteSessionRequest req) {
        Booking booking = bookingService.find(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        requireProviderOrAdmin(booking);
        Booking saved = bookingService.completeSession(booking, req.getNote());
        return ResponseEntity.ok(BookingResponse.from(saved));
    }

    @RequireVerified
    @PostMapping("/{id}/no-show")
    public ResponseEntity<BookingResponse> noShow(@PathVariable Long id, @Valid @RequestBody NoShowRequest req) {
        Booking booking = bookingService.find(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        boolean isAdmin = principal.isAdmin();
        boolean isBookingOwner = principal.userId() != null && principal.userId().equals(booking.getUserId());
        boolean isAssignedProvider = isAssignedProvider(principal, booking);

        // A user can only flag that the PROVIDER didn't show; a provider can only flag the USER. Admin can do either.
        boolean allowed = isAdmin
                || (req.getParty() == NoShowParty.PROVIDER && isBookingOwner)
                || (req.getParty() == NoShowParty.USER && isAssignedProvider);
        if (!allowed) {
            throw new AccessDeniedException("You may only report a no-show for the other party in your own booking.");
        }

        Booking saved = bookingService.recordNoShow(booking, principal.userId(), req.getParty(), req.getReason());
        return ResponseEntity.ok(BookingResponse.from(saved));
    }

    @RequireVerified
    @PatchMapping("/{id}/note")
    public ResponseEntity<BookingResponse> amendNote(@PathVariable Long id, @Valid @RequestBody AmendNoteRequest req) {
        Booking booking = bookingService.find(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        FirebaseUserPrincipal principal = requireProviderOrAdmin(booking);
        Booking saved = bookingService.amendNote(booking, principal.userId(), req.getNote(), req.getReason());
        return ResponseEntity.ok(BookingResponse.from(saved));
    }

    @RequireVerified
    @PostMapping("/{id}/feedback")
    public ResponseEntity<SessionFeedbackResponse> feedback(@PathVariable Long id, @Valid @RequestBody SubmitFeedbackRequest req) {
        Booking booking = bookingService.find(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireSelfOrAdmin(booking.getUserId());
        var feedback = bookingService.submitFeedback(booking, booking.getUserId(), req);
        return ResponseEntity.ok(SessionFeedbackResponse.from(feedback));
    }

    private FirebaseUserPrincipal requireProviderOrAdmin(Booking booking) {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        if (principal.isAdmin() || isAssignedProvider(principal, booking)) {
            return principal;
        }
        throw new AccessDeniedException("Only the assigned provider or an admin may perform this action.");
    }

    private boolean isAssignedProvider(FirebaseUserPrincipal principal, Booking booking) {
        return principal.userId() != null
                && providerService.findByUserId(principal.userId())
                        .map(p -> p.getId().equals(booking.getProviderId()))
                        .orElse(false);
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
