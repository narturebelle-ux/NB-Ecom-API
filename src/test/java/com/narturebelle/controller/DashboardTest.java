package com.narturebelle.controller;

import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.repository.JourneyRepository;
import com.narturebelle.repository.JourneyStageEventRepository;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 1.3: dashboard shape, journeySetupRequired, and late-enrolment windowing (Workflow D). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardTest {

    private static final String TOKEN = "dashboard-token";
    private static final String TOKEN_UNCONSENTED = "dashboard-token-unconsented";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private JourneyRepository journeyRepository;

    @Autowired
    private JourneyStageEventRepository journeyStageEventRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() throws Exception {
        journeyStageEventRepository.deleteAll();
        journeyRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFirebaseUid("uid-dashboard");
        user.setEmail("dashboard@example.com");
        user.setEmailVerified(true);
        user.setFirstName("Dash");
        user.setLastName("Board");
        user.setRoles(Set.of(Role.MOTHER));
        Long userId = userRepository.save(user).getId();
        grantAllConsents(userId);

        User unconsented = new User();
        unconsented.setFirebaseUid("uid-dashboard-unconsented");
        unconsented.setEmail("dashboard-unconsented@example.com");
        unconsented.setEmailVerified(true);
        unconsented.setRoles(Set.of(Role.MOTHER));
        userRepository.save(unconsented);

        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-dashboard", "dashboard@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_UNCONSENTED))
                .thenReturn(new VerifiedFirebaseToken("uid-dashboard-unconsented", "dashboard-unconsented@example.com", true, null, null));
    }

    private void grantAllConsents(Long userId) {
        for (ConsentDocumentType type : ConsentDocumentType.values()) {
            Consent consent = new Consent();
            consent.setUserId(userId);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }
    }

    @Test
    void unconsentedUser_returns403ConsentRequired() throws Exception {
        mockMvc.perform(get("/api/v1/me/dashboard").header("Authorization", "Bearer " + TOKEN_UNCONSENTED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void noJourney_setupRequiredAndEmptyActions() throws Exception {
        mockMvc.perform(get("/api/v1/me/dashboard").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.firstName").value("Dash"))
                .andExpect(jsonPath("$.journey").doesNotExist())
                .andExpect(jsonPath("$.journeySetupRequired").value(true))
                .andExpect(jsonPath("$.nextActions").isArray())
                .andExpect(jsonPath("$.nextActions").isEmpty());
    }

    @Test
    void lateEnrollment_atPreDelivery_neverShowsEarlierPregnancyTrimesters() throws Exception {
        mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRE_DELIVERY\",\"expectedDeliveryDate\":\"" + java.time.LocalDate.now().plusDays(14) + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/dashboard").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journeySetupRequired").value(false))
                .andExpect(jsonPath("$.nextActions[*].title",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Routine first-trimester prenatal check-up."))))
                .andExpect(jsonPath("$.nextActions[*].professionalType",
                        org.hamcrest.Matchers.hasItem("LACTATION_CONSULTANT")))
                .andExpect(jsonPath("$.nextActions[*].professionalType",
                        org.hamcrest.Matchers.hasItem("CLINICAL_PSYCHOLOGIST")));
    }

    @Test
    void newPregnancyJourney_earlyOn_showsFullRoadmapFromTrimester1() throws Exception {
        mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREGNANCY\",\"expectedDeliveryDate\":\"" + java.time.LocalDate.now().plusDays(260) + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/dashboard").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextActions[0].professionalType").value("GYNECOLOGIST"))
                .andExpect(jsonPath("$.nextActions[0].reviewStatus").value("PENDING_CLINICAL_REVIEW"))
                .andExpect(jsonPath("$.nextActions[*].professionalType",
                        org.hamcrest.Matchers.hasItem("NUTRITIONIST")))
                .andExpect(jsonPath("$.nextActions[*].optional", org.hamcrest.Matchers.hasItem(true)));
    }
}
