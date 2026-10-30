package com.narturebelle.controller;

import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.OnboardingCallSlot;
import com.narturebelle.model.Role;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.model.User;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.repository.OnboardingCallRepository;
import com.narturebelle.repository.OnboardingCallSlotRepository;
import com.narturebelle.repository.OnboardingCallbackRequestRepository;
import com.narturebelle.repository.OnboardingCompletionRepository;
import com.narturebelle.repository.OnboardingDocumentRepository;
import com.narturebelle.repository.OnboardingDocumentSignatureRepository;
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

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers the Onboarding intake, call-slot-booking, document-signing, and completion endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingTest {

    private static final String TOKEN_A = "onboarding-token-a";
    private static final String TOKEN_UNCONSENTED = "onboarding-token-unconsented";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private OnboardingIntakeRepository onboardingIntakeRepository;
    @Autowired private OnboardingCallSlotRepository onboardingCallSlotRepository;
    @Autowired private OnboardingCallRepository onboardingCallRepository;
    @Autowired private OnboardingCallbackRequestRepository onboardingCallbackRequestRepository;
    @Autowired private OnboardingDocumentRepository onboardingDocumentRepository;
    @Autowired private OnboardingDocumentSignatureRepository onboardingDocumentSignatureRepository;
    @Autowired private OnboardingCompletionRepository onboardingCompletionRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() throws Exception {
        onboardingCompletionRepository.deleteAll();
        onboardingDocumentSignatureRepository.deleteAll();
        onboardingCallbackRequestRepository.deleteAll();
        onboardingCallRepository.deleteAll();
        onboardingCallSlotRepository.deleteAll();
        onboardingIntakeRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        createConsentedUser("uid-onboarding-a", "onboarding-a@example.com");
        createUser("uid-onboarding-unconsented", "onboarding-unconsented@example.com");

        when(firebaseTokenVerifier.verify(TOKEN_A))
                .thenReturn(new VerifiedFirebaseToken("uid-onboarding-a", "onboarding-a@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_UNCONSENTED))
                .thenReturn(new VerifiedFirebaseToken("uid-onboarding-unconsented", "onboarding-unconsented@example.com", true, null, null));
    }

    private Long createUser(String uid, String email) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        return userRepository.save(user).getId();
    }

    private Long createConsentedUser(String uid, String email) {
        Long id = createUser(uid, email);
        for (ConsentDocumentType type : ConsentDocumentType.values()) {
            Consent consent = new Consent();
            consent.setUserId(id);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }
        return id;
    }

    @Test
    void unconsentedUser_hittingOnboarding_returns403ConsentRequired() throws Exception {
        mockMvc.perform(get("/api/v1/me/onboarding").header("Authorization", "Bearer " + TOKEN_UNCONSENTED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void status_withNoIntakeOrCall_returnsNulls() throws Exception {
        mockMvc.perform(get("/api/v1/me/onboarding").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intake").doesNotExist())
                .andExpect(jsonPath("$.call").doesNotExist())
                .andExpect(jsonPath("$.documentsSigned").value(false));
    }

    @Test
    void upsertIntake_createsThenUpdates_reflectedInStatus() throws Exception {
        mockMvc.perform(put("/api/v1/me/onboarding/intake")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstPregnancy\":true,\"multiples\":false,"
                                + "\"riskFactors\":[\"DIABETES\",\"THYROID\"],"
                                + "\"supportWanted\":[\"Nutrition guidance\"],"
                                + "\"city\":\"Bengaluru\",\"language\":\"English\","
                                + "\"preferredModality\":\"VIRTUAL\",\"phone\":\"9999999999\",\"notes\":\"first note\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Bengaluru"))
                .andExpect(jsonPath("$.riskFactors", org.hamcrest.Matchers.containsInAnyOrder("DIABETES", "THYROID")));

        assertEquals(1, onboardingIntakeRepository.count());

        mockMvc.perform(put("/api/v1/me/onboarding/intake")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstPregnancy\":true,\"multiples\":false,"
                                + "\"riskFactors\":[],\"supportWanted\":[],"
                                + "\"city\":\"Mumbai\",\"language\":\"English\","
                                + "\"preferredModality\":\"PHYSICAL\",\"phone\":\"9999999999\",\"notes\":\"updated note\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Mumbai"));

        assertEquals(1, onboardingIntakeRepository.count());

        mockMvc.perform(get("/api/v1/me/onboarding").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intake.city").value("Mumbai"));
    }

    @Test
    void callSlots_listsOnlyOpenFutureSlots() throws Exception {
        OnboardingCallSlot open = new OnboardingCallSlot();
        open.setStartAt(LocalDateTime.now().plusDays(1));
        open.setEndAt(LocalDateTime.now().plusDays(1).plusMinutes(30));
        open.setStatus(SlotStatus.OPEN);
        onboardingCallSlotRepository.save(open);

        OnboardingCallSlot booked = new OnboardingCallSlot();
        booked.setStartAt(LocalDateTime.now().plusDays(2));
        booked.setEndAt(LocalDateTime.now().plusDays(2).plusMinutes(30));
        booked.setStatus(SlotStatus.BOOKED);
        onboardingCallSlotRepository.save(booked);

        OnboardingCallSlot past = new OnboardingCallSlot();
        past.setStartAt(LocalDateTime.now().minusDays(1));
        past.setEndAt(LocalDateTime.now().minusDays(1).plusMinutes(30));
        past.setStatus(SlotStatus.OPEN);
        onboardingCallSlotRepository.save(past);

        mockMvc.perform(get("/api/v1/onboarding/call-slots").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(open.getId()));
    }

    @Test
    void bookCall_happyPath_claimsSlotAndReflectedInStatus() throws Exception {
        OnboardingCallSlot slot = new OnboardingCallSlot();
        slot.setStartAt(LocalDateTime.now().plusDays(1));
        slot.setEndAt(LocalDateTime.now().plusDays(1).plusMinutes(30));
        slot.setStatus(SlotStatus.OPEN);
        slot = onboardingCallSlotRepository.save(slot);

        mockMvc.perform(post("/api/v1/me/onboarding/call")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + slot.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(slot.getId()));

        mockMvc.perform(get("/api/v1/me/onboarding").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.call.slotId").value(slot.getId()));
    }

    @Test
    void bookCall_alreadyBooked_returns409() throws Exception {
        OnboardingCallSlot slot1 = new OnboardingCallSlot();
        slot1.setStartAt(LocalDateTime.now().plusDays(1));
        slot1.setEndAt(LocalDateTime.now().plusDays(1).plusMinutes(30));
        slot1.setStatus(SlotStatus.OPEN);
        slot1 = onboardingCallSlotRepository.save(slot1);

        OnboardingCallSlot slot2 = new OnboardingCallSlot();
        slot2.setStartAt(LocalDateTime.now().plusDays(2));
        slot2.setEndAt(LocalDateTime.now().plusDays(2).plusMinutes(30));
        slot2.setStatus(SlotStatus.OPEN);
        slot2 = onboardingCallSlotRepository.save(slot2);

        mockMvc.perform(post("/api/v1/me/onboarding/call")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + slot1.getId() + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/onboarding/call")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + slot2.getId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_CALL_ALREADY_BOOKED"));
    }

    @Test
    void bookCall_slotAlreadyTaken_returns409() throws Exception {
        OnboardingCallSlot slot = new OnboardingCallSlot();
        slot.setStartAt(LocalDateTime.now().plusDays(1));
        slot.setEndAt(LocalDateTime.now().plusDays(1).plusMinutes(30));
        slot.setStatus(SlotStatus.BOOKED);
        slot = onboardingCallSlotRepository.save(slot);

        mockMvc.perform(post("/api/v1/me/onboarding/call")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + slot.getId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_CALL_SLOT_NOT_AVAILABLE"));
    }

    @Test
    void adminCreateCallSlots_requiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/onboarding/call-slots")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slots\":[{\"startAt\":\"" + LocalDateTime.now().plusDays(3)
                                + "\",\"endAt\":\"" + LocalDateTime.now().plusDays(3).plusMinutes(30) + "\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestCallback_createsRecord_visibleToAdminOnly() throws Exception {
        mockMvc.perform(post("/api/v1/me/onboarding/callback-request")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Please call after 6pm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("Please call after 6pm"));

        assertEquals(1, onboardingCallbackRequestRepository.findAllByOrderByCreatedAtDesc().size());

        mockMvc.perform(get("/api/v1/admin/onboarding/callback-requests")
                        .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isForbidden());
    }

    @Test
    void documents_listsActiveDocument_withSignedFlag() throws Exception {
        mockMvc.perform(get("/api/v1/me/onboarding/documents").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("ONBOARDING_AGREEMENT"))
                .andExpect(jsonPath("$[0].signed").value(false));
    }

    @Test
    void signDocument_createsSignature_idempotentOnReSign() throws Exception {
        Long documentId = onboardingDocumentRepository.findByActiveTrue().get(0).getId();

        mockMvc.perform(post("/api/v1/me/onboarding/documents/sign")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":" + documentId + ",\"typedName\":\"Jane Doe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typedName").value("Jane Doe"));

        assertEquals(1, onboardingDocumentSignatureRepository.findByUserId(userIdFor(TOKEN_A)).size());

        // Re-signing is a no-op, not a second row or an error.
        mockMvc.perform(post("/api/v1/me/onboarding/documents/sign")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":" + documentId + ",\"typedName\":\"Jane Doe Again\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typedName").value("Jane Doe"));

        assertEquals(1, onboardingDocumentSignatureRepository.findByUserId(userIdFor(TOKEN_A)).size());

        mockMvc.perform(get("/api/v1/me/onboarding/documents").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signed").value(true));
    }

    @Test
    void signDocument_unknownDocument_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/me/onboarding/documents/sign")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":999999,\"typedName\":\"Jane Doe\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ONBOARDING_DOCUMENT_NOT_FOUND"));
    }

    @Test
    void complete_missingEverything_returns409WithAllMissingSteps() throws Exception {
        mockMvc.perform(post("/api/v1/me/onboarding/complete").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_INCOMPLETE"))
                .andExpect(jsonPath("$.missingSteps", org.hamcrest.Matchers.containsInAnyOrder(
                        "intake", "onboarding call", "payment", "document signatures")));
    }

    @Test
    void complete_happyPath_withCallbackRequestInsteadOfCall_isIdempotent() throws Exception {
        mockMvc.perform(put("/api/v1/me/onboarding/intake")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"Bengaluru\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/onboarding/callback-request")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        String orderBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = Long.valueOf(orderBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        String paymentBody = mockMvc.perform(post("/api/v1/payments/create")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long paymentId = Long.valueOf(paymentBody.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk());

        Long documentId = onboardingDocumentRepository.findByActiveTrue().get(0).getId();
        mockMvc.perform(post("/api/v1/me/onboarding/documents/sign")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":" + documentId + ",\"typedName\":\"Jane Doe\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/onboarding/complete").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").exists());

        assertEquals(1, onboardingCompletionRepository.count());
        Long completionId = onboardingCompletionRepository.findByUserId(userIdFor(TOKEN_A)).orElseThrow().getId();

        // Completing again must not create a second row -- same completion id, not a new one.
        mockMvc.perform(post("/api/v1/me/onboarding/complete").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk());

        assertEquals(1, onboardingCompletionRepository.count());
        assertEquals(completionId, onboardingCompletionRepository.findByUserId(userIdFor(TOKEN_A)).orElseThrow().getId());

        mockMvc.perform(get("/api/v1/me/onboarding").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsSigned").value(true))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    private Long userIdFor(String token) {
        return userRepository.findByFirebaseUid(token.equals(TOKEN_A) ? "uid-onboarding-a" : "uid-onboarding-unconsented")
                .orElseThrow().getId();
    }
}
