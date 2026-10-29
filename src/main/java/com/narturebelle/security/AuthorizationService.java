package com.narturebelle.security;

import com.narturebelle.repository.ProviderRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Reusable ownership/identity checks — reuse requireSelfOrAdmin for future cart/order endpoints too. */
@Component
public class AuthorizationService {

    private final ProviderRepository providerRepository;

    public AuthorizationService(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
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
}
