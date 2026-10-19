package com.narturebelle.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Reusable ownership/identity checks — reuse requireSelfOrAdmin for future cart/order endpoints too. */
@Component
public class AuthorizationService {

    public FirebaseUserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof FirebaseUserPrincipal principal)) {
            throw new AccessDeniedException("No authenticated user in context.");
        }
        return principal;
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
}
