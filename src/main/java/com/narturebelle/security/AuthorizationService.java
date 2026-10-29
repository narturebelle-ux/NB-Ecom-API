package com.narturebelle.security;

import com.narturebelle.model.Booking;
import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.Provider;
import com.narturebelle.model.RecordAccessRelationship;
import com.narturebelle.model.RecordShare;
import com.narturebelle.repository.BookingRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.RecordShareRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Reusable ownership/identity checks — reuse requireSelfOrAdmin for future cart/order endpoints too. */
@Component
public class AuthorizationService {

    private final ProviderRepository providerRepository;
    private final RecordShareRepository recordShareRepository;
    private final BookingRepository bookingRepository;

    public AuthorizationService(ProviderRepository providerRepository, RecordShareRepository recordShareRepository,
                                 BookingRepository bookingRepository) {
        this.providerRepository = providerRepository;
        this.recordShareRepository = recordShareRepository;
        this.bookingRepository = bookingRepository;
    }

    public FirebaseUserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof FirebaseUserPrincipal principal)) {
            throw new AccessDeniedException("No authenticated user in context.");
        }
        return principal;
    }

    /** Same as currentPrincipal() but never throws — for callers like AuditService that may run outside a request. */
    public Optional<FirebaseUserPrincipal> currentPrincipalOrEmpty() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof FirebaseUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /** Throws AccessDeniedException (-> 403 FORBIDDEN) unless the caller is the target user or an admin. */
    public void requireSelfOrAdmin(Long targetUserId) {
        FirebaseUserPrincipal principal = currentPrincipal();
        if (principal.isAdmin()) {
            return;
        }
        if (principal.userId() == null || !principal.userId().equals(targetUserId)) {
            throw new AccessDeniedException("You may only access your own resource.");
        }
    }

    /**
     * Three-way booking access: admin, the booking's own user, or the Provider assigned to it.
     * Throws AccessDeniedException (-> 403 FORBIDDEN) otherwise.
     */
    public void requireBookingAccess(Long bookingUserId, Long bookingProviderId) {
        FirebaseUserPrincipal principal = currentPrincipal();
        if (principal.isAdmin()) {
            return;
        }
        if (principal.userId() != null && principal.userId().equals(bookingUserId)) {
            return;
        }
        boolean isAssignedProvider = principal.userId() != null
                && providerRepository.findByUserId(principal.userId())
                        .map(provider -> provider.getId().equals(bookingProviderId))
                        .orElse(false);
        if (!isAssignedProvider) {
            throw new AccessDeniedException("You may only access your own bookings.");
        }
    }

    /**
     * Record access: admin, the record's own owner, or someone holding a currently-valid
     * (non-expired, non-revoked) share — directly as a Provider, or via a Booking they're
     * currently assigned to (looked up live, same resolution requireBookingAccess already uses).
     * Throws AccessDeniedException (-> 403 FORBIDDEN) otherwise.
     */
    public RecordAccessResult requireRecordAccess(HealthRecord record) {
        FirebaseUserPrincipal principal = currentPrincipal();
        if (principal.isAdmin()) {
            return new RecordAccessResult(RecordAccessRelationship.ADMIN, null);
        }
        if (principal.userId() != null && principal.userId().equals(record.getUserId())) {
            return new RecordAccessResult(RecordAccessRelationship.OWNER, null);
        }

        if (principal.userId() != null) {
            Provider callerProvider = providerRepository.findByUserId(principal.userId()).orElse(null);
            if (callerProvider != null) {
                LocalDateTime now = LocalDateTime.now();
                List<RecordShare> activeShares = recordShareRepository.findByRecordIdAndRevokedAtIsNull(record.getId())
                        .stream()
                        .filter(share -> share.getExpiresAt().isAfter(now))
                        .toList();
                for (RecordShare share : activeShares) {
                    if (callerProvider.getId().equals(share.getTargetProviderId())) {
                        return new RecordAccessResult(RecordAccessRelationship.SHARED_PROVIDER, share.getId());
                    }
                    if (share.getTargetBookingId() != null) {
                        Booking booking = bookingRepository.findById(share.getTargetBookingId()).orElse(null);
                        if (booking != null && callerProvider.getId().equals(booking.getProviderId())) {
                            return new RecordAccessResult(RecordAccessRelationship.SHARED_PROVIDER, share.getId());
                        }
                    }
                }
            }
        }

        throw new AccessDeniedException("You do not have access to this record.");
    }

    public record RecordAccessResult(RecordAccessRelationship relationship, Long matchedShareId) {}
}
