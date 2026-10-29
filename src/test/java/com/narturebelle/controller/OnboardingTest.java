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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers the Onboarding intake + call-slot-booking endpoints (Part A -- documents/completion land after Phase 5). */
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

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() throws Exception {
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
}
