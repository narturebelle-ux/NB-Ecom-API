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

import java.time.LocalDate;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 1.2: journey create/409/patch/ownership, gestational-week maths, and the CONSENT_REQUIRED gate. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JourneyTest {

    private static final String TOKEN_A = "journey-token-a";
    private static final String TOKEN_B = "journey-token-b";
    private static final String TOKEN_UNCONSENTED = "journey-token-unconsented";
    private static final String TOKEN_ADMIN = "journey-token-admin";

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

    private Long userAId;
    private Long userBId;

    @BeforeEach
    void setUp() throws Exception {
        journeyStageEventRepository.deleteAll();
        journeyRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        userAId = createConsentedUser("uid-journey-a", "journey-a@example.com", Role.MOTHER);
        userBId = createConsentedUser("uid-journey-b", "journey-b@example.com", Role.MOTHER);
        Long unconsentedId = createUser("uid-journey-unconsented", "journey-unconsented@example.com", Role.MOTHER);
        Long adminId = createConsentedUser("uid-journey-admin", "journey-admin@example.com", Role.SUPER_ADMIN);

        when(firebaseTokenVerifier.verify(TOKEN_A))
                .thenReturn(new VerifiedFirebaseToken("uid-journey-a", "journey-a@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_B))
                .thenReturn(new VerifiedFirebaseToken("uid-journey-b", "journey-b@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_UNCONSENTED))
                .thenReturn(new VerifiedFirebaseToken("uid-journey-unconsented", "journey-unconsented@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-journey-admin", "journey-admin@example.com", true, null, null));
    }

    private Long createUser(String uid, String email, Role role) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(role));
        return userRepository.save(user).getId();
    }

    private Long createConsentedUser(String uid, String email, Role role) {
        Long id = createUser(uid, email, role);
        grantAllConsents(id);
        return id;
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
    void unconsentedUser_creatingJourney_returns403ConsentRequired() throws Exception {
        mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN_UNCONSENTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREGNANCY\",\"expectedDeliveryDate\":\"" + java.time.LocalDate.now().plusDays(100) + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void create_pregnancyWithoutDueDate_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREGNANCY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.expectedDeliveryDate").exists());
    }

    @Test
    void create_withEdd_computesGestationalWeekAndTrimester() throws Exception {
        // EDD 280 days from today => day0 == today => week 1, trimester 1.
        String edd = LocalDate.now().plusDays(280).toString();
        mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREGNANCY\",\"expectedDeliveryDate\":\"" + edd + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gestationalWeek").value(1))
                .andExpect(jsonPath("$.trimester").value(1));
    }

    @Test
    void create_withEddTooFarInFuture_returns400() throws Exception {
        String edd = LocalDate.now().plusWeeks(43).toString();
        mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREGNANCY\",\"expectedDeliveryDate\":\"" + edd + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void create_secondTimeWithoutClosingFirst_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREGNANCY\",\"expectedDeliveryDate\":\"" + java.time.LocalDate.now().plusDays(100) + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREGNANCY\",\"expectedDeliveryDate\":\"" + java.time.LocalDate.now().plusDays(100) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOURNEY_ALREADY_ACTIVE"));
    }

    @Test
    void getOne_byAnotherUser_returns403_butAdminCanAccess() throws Exception {
        String body = mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREGNANCY\",\"expectedDeliveryDate\":\"" + java.time.LocalDate.now().plusDays(100) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long journeyId = Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/v1/me/journeys/" + journeyId).header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/me/journeys/" + journeyId).header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(journeyId));
    }

    @Test
    void patch_statusChange_recordsStageEventAndUpdatesHasActiveJourney() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(jsonPath("$.hasActiveJourney").value(false));

        String body = mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREGNANCY\",\"expectedDeliveryDate\":\"" + java.time.LocalDate.now().plusDays(100) + "\"}"))
                .andReturn().getResponse().getContentAsString();
        Long journeyId = Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(jsonPath("$.hasActiveJourney").value(true));

        mockMvc.perform(patch("/api/v1/me/journeys/" + journeyId)
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRE_DELIVERY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRE_DELIVERY"));

        org.junit.jupiter.api.Assertions.assertEquals(1, journeyStageEventRepository.findAll().stream()
                .filter(e -> e.getJourneyId().equals(journeyId) && e.getToStatus().name().equals("PRE_DELIVERY"))
                .count());
    }

    @Test
    void patch_toClosed_allowsCreatingANewJourney() throws Exception {
        String body = mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREGNANCY\",\"expectedDeliveryDate\":\"" + java.time.LocalDate.now().plusDays(100) + "\"}"))
                .andReturn().getResponse().getContentAsString();
        Long journeyId = Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(patch("/api/v1/me/journeys/" + journeyId)
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedAt").exists());

        mockMvc.perform(post("/api/v1/me/journeys")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREGNANCY\",\"expectedDeliveryDate\":\"" + java.time.LocalDate.now().plusDays(100) + "\"}"))
                .andExpect(status().isOk());
    }
}
