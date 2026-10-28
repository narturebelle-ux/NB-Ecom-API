package com.narturebelle.security;

import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Verifies the Authorization: Bearer <Firebase ID token> header on every request via the injected
 * FirebaseTokenVerifier (swappable in tests) and, when present and valid, populates the
 * SecurityContext with a FirebaseUserPrincipal. Absent/invalid tokens leave the request anonymous —
 * the SecurityFilterChain's matchers decide whether that's allowed (permitAll) or rejected
 * (401 via RestAuthenticationEntryPoint). Dev-bypass behavior lives in DefaultFirebaseTokenVerifier,
 * not here, so swapping in a test/mock verifier always takes full control of authentication.
 */
@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    private final FirebaseTokenVerifier tokenVerifier;
    private final UserRepository userRepository;

    public FirebaseAuthenticationFilter(FirebaseTokenVerifier tokenVerifier, UserRepository userRepository) {
        this.tokenVerifier = tokenVerifier;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                authenticate(token);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            VerifiedFirebaseToken verified = tokenVerifier.verify(token);
            Optional<User> dbUser = userRepository.findByFirebaseUid(verified.uid());
            Long userId = dbUser.map(User::getId).orElse(null);
            String role = dbUser.map(User::getRole).orElse("USER");

            FirebaseUserPrincipal principal = new FirebaseUserPrincipal(
                    userId, verified.uid(), verified.email(), verified.emailVerified(),
                    verified.phone(), verified.phone() != null, role, verified.name()
            );

            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            if (principal.isAdmin()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            if (principal.isVerified()) {
                authorities.add(new SimpleGrantedAuthority("VERIFIED"));
            }
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
        } catch (Exception e) {
            log.debug("Firebase token verification failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }
}
