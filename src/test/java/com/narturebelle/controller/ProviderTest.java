package com.narturebelle.controller;

import com.narturebelle.model.ProviderSlot;
import com.narturebelle.model.Role;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.model.User;
import com.narturebelle.repository.ProviderCredentialRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.ProviderSlotRepository;
import com.narturebelle.repository.ServiceOfferingRepository;
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

/** Covers 3.1/3.2: provider directory, admin onboarding, and the verify-grants-role-with-no-new-token behavior. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProviderTest {

    private static final String TOKEN_ADMIN = "provider-token-admin";
    private static final String TOKEN_CANDIDATE = "provider-token-candidate";
    private static final String TOKEN_NON_ADMIN = "provider-token-non-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ProviderCredentialRepository providerCredentialRepository;

    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;

    @Autowired
    private ProviderSlotRepository providerSlotRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long candidateUserId;

    @BeforeEach
    void setUp() throws Exception {
        providerSlotRepository.deleteAll();
        serviceOfferingRepository.deleteAll();
        providerCredentialRepository.deleteAll();
        providerRepository.deleteAll();
        userRepository.deleteAll();

        User admin = new User();
        admin.setFirebaseUid("uid-provider-admin");
        admin.setEmail("provider-admin@example.com");
        admin.setEmailVerified(true);
        admin.setRoles(Set.of(Role.SUPER_ADMIN));
        userRepository.save(admin);

        User candidate = new User();
        candidate.setFirebaseUid("uid-provider-candidate");
        candidate.setEmail("provider-candidate@example.com");
        candidate.setEmailVerified(true);
        candidate.setRoles(Set.of(Role.MOTHER));
        candidateUserId = userRepository.save(candidate).getId();

        User nonAdmin = new User();
        nonAdmin.setFirebaseUid("uid-provider-nonadmin");
        nonAdmin.setEmail("provider-nonadmin@example.com");
        nonAdmin.setEmailVerified(true);
        nonAdmin.setRoles(Set.of(Role.MOTHER));
        userRepository.save(nonAdmin);

        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-provider-admin", "provider-admin@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_CANDIDATE))
                .thenReturn(new VerifiedFirebaseToken("uid-provider-candidate", "provider-candidate@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_NON_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-provider-nonadmin", "provider-nonadmin@example.com", true, null, null));
    }

    private Long createProvider() throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/providers")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + candidateUserId + ",\"professionalType\":\"GYNECOLOGIST\","
                                + "\"displayName\":\"Dr. Test\",\"city\":\"Bengaluru\",\"languages\":[\"English\",\"Hindi\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVITED"))
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void create_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/providers")
                        .header("Authorization", "Bearer " + TOKEN_NON_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + candidateUserId + ",\"professionalType\":\"GYNECOLOGIST\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void verify_requiresUnderReview_elseConflict() throws Exception {
        Long providerId = createProvider();

        mockMvc.perform(post("/api/v1/admin/providers/" + providerId + "/verify")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"docs look fine\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROVIDER_STATUS_TRANSITION"));
    }

    @Test
    void verify_grantsRole_effectiveOnNextRequest_noNewToken() throws Exception {
        Long providerId = createProvider();

        // Walk through the legal status chain to UNDER_REVIEW.
        mockMvc.perform(patch("/api/v1/admin/providers/" + providerId + "/status")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DOCUMENTS_PENDING\",\"reason\":\"collecting docs\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/providers/" + providerId + "/status")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNDER_REVIEW\",\"reason\":\"docs submitted\"}"))
                .andExpect(status().isOk());

        // Before verify: candidate's /me shows only MOTHER.
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + TOKEN_CANDIDATE))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.contains("MOTHER")));

        mockMvc.perform(post("/api/v1/admin/providers/" + providerId + "/verify")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        // Same candidate token, no new Firebase ID token — the role is already there.
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + TOKEN_CANDIDATE))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("MOTHER", "GYNECOLOGIST")));
    }

    @Test
    void updateStatus_rejectsIllegalTransitions_acceptsLegalOnes() throws Exception {
        Long providerId = createProvider();

        mockMvc.perform(patch("/api/v1/admin/providers/" + providerId + "/status")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"reason\":\"skip ahead\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROVIDER_STATUS_TRANSITION"));

        mockMvc.perform(patch("/api/v1/admin/providers/" + providerId + "/status")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DOCUMENTS_PENDING\",\"reason\":\"collecting docs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOCUMENTS_PENDING"));
    }

    @Test
    void getProviders_onlyVerifiedAndActive_respectsFilters() throws Exception {
        Long providerId = createProvider();
        verifyProvider(providerId);

        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].professionalType").value("GYNECOLOGIST"));

        mockMvc.perform(get("/api/v1/providers?professionalType=NUTRITIONIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        mockMvc.perform(get("/api/v1/providers?city=Bengaluru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(get("/api/v1/providers?language=Tamil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void getSlots_excludesBookedAndPastSlots() throws Exception {
        Long providerId = createProvider();
        verifyProvider(providerId);

        ProviderSlot future = new ProviderSlot();
        future.setProviderId(providerId);
        future.setStartAt(LocalDateTime.now().plusDays(1));
        future.setEndAt(LocalDateTime.now().plusDays(1).plusMinutes(30));
        future.setStatus(SlotStatus.OPEN);
        providerSlotRepository.save(future);

        ProviderSlot booked = new ProviderSlot();
        booked.setProviderId(providerId);
        booked.setStartAt(LocalDateTime.now().plusDays(2));
        booked.setEndAt(LocalDateTime.now().plusDays(2).plusMinutes(30));
        booked.setStatus(SlotStatus.BOOKED);
        providerSlotRepository.save(booked);

        ProviderSlot past = new ProviderSlot();
        past.setProviderId(providerId);
        past.setStartAt(LocalDateTime.now().minusDays(1));
        past.setEndAt(LocalDateTime.now().minusDays(1).plusMinutes(30));
        past.setStatus(SlotStatus.OPEN);
        providerSlotRepository.save(past);

        mockMvc.perform(get("/api/v1/providers/" + providerId + "/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    private void verifyProvider(Long providerId) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/providers/" + providerId + "/status")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DOCUMENTS_PENDING\",\"reason\":\"x\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/providers/" + providerId + "/status")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNDER_REVIEW\",\"reason\":\"x\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/providers/" + providerId + "/verify")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isOk());
    }
}
