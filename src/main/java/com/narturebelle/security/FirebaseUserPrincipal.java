package com.narturebelle.security;

/**
 * Authentication principal built from a verified Firebase token plus our DB's User row (if any).
 * userId is null when the token is valid but the user hasn't called POST /api/v1/auth/firebase yet.
 */
public record FirebaseUserPrincipal(
        Long userId,
        String firebaseUid,
        String email,
        boolean emailVerified,
        String phone,
        boolean phoneVerified,
        String role,
        String name
) {
    public boolean isVerified() {
        return emailVerified || phone != null;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
