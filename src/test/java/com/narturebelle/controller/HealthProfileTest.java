package com.narturebelle.controller;

import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.AuditEventRepository;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.repository.HealthProfileRepository;
import com.narturebelle.repository.OnboardingIntakeRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers the separate health profile and its HEALTH_DATA consent. The user here has TERMS + PRIVACY only. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthProfileTest {

    private static final String TOKEN = "health-token";
    private static final String PROFILE = "{\"firstPregnancy\":true,\"multiples\":false,\"riskFactors\":[\"DIABETES\"]}";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private HealthProfileRepository healthProfileRepository;
    @Autowired private OnboardingIntakeRepository onboardingIntakeRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        healthProfileRepository.deleteAll();
        onboardingIntakeRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFirebaseUid("uid-health");
        user.setEmail("health@example.com");
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        userId = userRepository.save(user).getId();
        grant(ConsentDocumentType.TERMS);
        grant(ConsentDocumentType.PRIVACY);

        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-health", "health@example.com", true, null, null));
    }

    private void grant(ConsentDocumentType type) {
        Consent consent = new Consent();
        consent.setUserId(userId);
        consent.setDocumentType(type);
        consent.setDocumentVersion(type == ConsentDocumentType.HEALTH_DATA ? 2 : 1);
        consentRepository.save(consent);
    }

    @Test
    void withoutHealthDataConsent_putIs403_andNothingIsSaved_viaEitherRoute() throws Exception {
        mockMvc.perform(put("/api/v1/me/health-profile").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"))
                .andExpect(jsonPath("$.pending[0]").value("HEALTH_DATA"));

        // An older client sending health details on the intake is refused the same way, before the intake is saved.
        mockMvc.perform(put("/api/v1/me/onboarding/intake").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"Pune\",\"riskFactors\":[\"THYROID\"]}"))
                .andExpect(status().isForbidden());

        assertEquals(0, healthProfileRepository.count());
        assertEquals(0, onboardingIntakeRepository.count());

        // Without health fields the intake still saves fine.
        mockMvc.perform(put("/api/v1/me/onboarding/intake").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"Pune\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void healthData_isNotARequiredConsent() throws Exception {
        mockMvc.perform(get("/api/v1/me/consents").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void withConsent_putReplacesProfile_getReturnsIt_andBothAreAudited_withoutHealthContent() throws Exception {
        grant(ConsentDocumentType.HEALTH_DATA);

        mockMvc.perform(get("/api/v1/me/health-profile").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HEALTH_PROFILE_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/me/health-profile").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskFactors[0]").value("DIABETES"));

        // Full replacement: omitted fields are cleared.
        mockMvc.perform(put("/api/v1/me/health-profile").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"multiples\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/health-profile").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.multiples").value(true))
                .andExpect(jsonPath("$.firstPregnancy").doesNotExist())
                .andExpect(jsonPath("$.riskFactors", org.hamcrest.Matchers.hasSize(0)));

        assertEquals(1, healthProfileRepository.count());
        var events = auditEventRepository.findAll().stream()
                .filter(e -> e.getAction().startsWith("HEALTH_PROFILE_"))
                .toList();
        assertTrue(events.stream().anyMatch(e -> e.getAction().equals("HEALTH_PROFILE_UPDATED")));
        assertTrue(events.stream().anyMatch(e -> e.getAction().equals("HEALTH_PROFILE_VIEWED")));
        assertFalse(events.stream().anyMatch(e -> e.getMetadataJson() != null && e.getMetadataJson().contains("DIABETES")));
    }
}
