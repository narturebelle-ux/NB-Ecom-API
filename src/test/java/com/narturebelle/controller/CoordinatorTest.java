package com.narturebelle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.repository.CoordinatorRepository;
import com.narturebelle.repository.OnboardingCallRepository;
import com.narturebelle.repository.OnboardingCallSlotRepository;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers onboarding coordinators ("Belle persons") and language-matched call slots. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoordinatorTest {

    private static final String TOKEN_USER = "coordinator-token-user";
    private static final String TOKEN_ADMIN = "coordinator-token-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private OnboardingIntakeRepository onboardingIntakeRepository;
    @Autowired private OnboardingCallRepository onboardingCallRepository;
    @Autowired private OnboardingCallSlotRepository onboardingCallSlotRepository;
    @Autowired private CoordinatorRepository coordinatorRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() throws Exception {
        onboardingCallRepository.deleteAll();
        onboardingCallSlotRepository.deleteAll();
        coordinatorRepository.deleteAll();
        onboardingIntakeRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        createUser("uid-coord-user", "coord-user@example.com", Role.MOTHER);
        createUser("uid-coord-admin", "coord-admin@example.com", Role.SUPER_ADMIN);
        when(firebaseTokenVerifier.verify(TOKEN_USER))
                .thenReturn(new VerifiedFirebaseToken("uid-coord-user", "coord-user@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-coord-admin", "coord-admin@example.com", true, null, null));
    }

    private void createUser(String uid, String email, Role role) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(role));
        Long id = userRepository.save(user).getId();
        for (ConsentDocumentType type : ConsentDocumentType.values()) {
            Consent consent = new Consent();
            consent.setUserId(id);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }
    }

    private long coordinator(String name, String languagesJson) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/coordinators").header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + name + "\",\"languages\":" + languagesJson + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long slot(long coordinatorId, int inDays) throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(inDays).withNano(0);
        String body = mockMvc.perform(post("/api/v1/admin/onboarding/call-slots").header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slots\":[{\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plusMinutes(30)
                                + "\",\"coordinatorId\":" + coordinatorId + "}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].coordinator.id").value(coordinatorId))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get(0).get("id").asLong();
    }

    private void intakeLanguages(String languagesJson) throws Exception {
        mockMvc.perform(put("/api/v1/me/onboarding/intake").header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"languages\":" + languagesJson + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void slots_areFilteredToCoordinatorsWhoSpeakHerLanguage_andTheCallShowsWhoWillCall() throws Exception {
        long tamil = coordinator("Kavya", "[\"English\",\"Tamil\"]");
        long hindi = coordinator("Riya", "[\"Hindi\"]");
        long tamilSlot = slot(tamil, 2);
        slot(hindi, 1);

        // No languages yet: every slot.
        mockMvc.perform(get("/api/v1/onboarding/call-slots").header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));

        intakeLanguages("[\"tamil\"]");
        mockMvc.perform(get("/api/v1/onboarding/call-slots").header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(tamilSlot))
                .andExpect(jsonPath("$[0].coordinator.displayName").value("Kavya"))
                .andExpect(jsonPath("$[0].coordinator.languages[1]").value("Tamil"))
                .andExpect(jsonPath("$[0].coordinator.active").doesNotExist());

        mockMvc.perform(post("/api/v1/me/onboarding/call").header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + tamilSlot + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coordinator.displayName").value("Kavya"));
        mockMvc.perform(get("/api/v1/me/onboarding").header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(jsonPath("$.call.coordinator.displayName").value("Kavya"));
    }

    @Test
    void deactivatedCoordinator_slotsAreHidden() throws Exception {
        long tamil = coordinator("Kavya", "[\"Tamil\"]");
        slot(tamil, 2);
        intakeLanguages("[\"Tamil\"]");

        mockMvc.perform(patch("/api/v1/admin/coordinators/" + tamil).header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/v1/onboarding/call-slots").header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void slotCreation_needsAnActiveCoordinator() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        mockMvc.perform(post("/api/v1/admin/onboarding/call-slots").header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slots\":[{\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plusMinutes(30) + "\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/onboarding/call-slots").header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slots\":[{\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plusMinutes(30)
                                + "\",\"coordinatorId\":999999}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COORDINATOR_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/admin/coordinators").header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(status().isForbidden());
    }
}
