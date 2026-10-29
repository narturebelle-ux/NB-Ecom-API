package com.narturebelle.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(FirebaseAuthenticationFilter firebaseAuthenticationFilter,
                           RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                           RestAccessDeniedHandler restAccessDeniedHandler,
                           CorsConfigurationSource corsConfigurationSource) {
        this.firebaseAuthenticationFilter = firebaseAuthenticationFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Public: browsing/content + lead-gen forms (no user concept involved today) + health.
                        .requestMatchers(
                                "/health/**",
                                "/api/v1/products/**",
                                "/api/v1/checklists/**",
                                "/api/v1/hospitals/**",
                                "/api/v1/features",
                                "/api/v1/doctor-sessions/**",
                                "/api/v1/nurse-companion/**",
                                "/api/v1/leads/**",
                                "/api/v1/consent-documents/**",
                                "/api/v1/plans/**",
                                "/api/v1/payments/webhook",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Dev-only legacy endpoints (bean only exists outside prod/stage; still explicit here).
                        .requestMatchers("/api/v1/auth/send-otp", "/api/v1/auth/login").permitAll()
                        // Admin namespace (feature flags, refunds, finance view, ...). Any *_ADMIN role qualifies.
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority(
                                "ROLE_CLINIC_ADMIN", "ROLE_HOSPITAL_ADMIN", "ROLE_OPERATIONS_ADMIN",
                                "ROLE_FINANCE_ADMIN", "ROLE_SECURITY_ADMIN", "ROLE_SUPER_ADMIN"
                        )
                        // Everything else (auth/firebase, users/me, users/{id}, future endpoints) needs a valid token.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
