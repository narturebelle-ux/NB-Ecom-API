package com.narturebelle.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.repository.HospitalPartnerRepository;
import com.narturebelle.repository.HospitalReferralRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers hospital referrals with attribution codes (Phase 6). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HospitalReferralTest {

    private static final String TOKEN_A = "referral-token-a";
    private static final String TOKEN_B = "referral-token-b";
    private static final String TOKEN_UNVERIFIED = "referral-token-unverified";
    private static final String TOKEN_ADMIN = "referral-token-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private HospitalPartnerRepository hospitalPartnerRepository;
    @Autowired private HospitalReferralRepository hospitalReferralRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long hospitalId;

    @BeforeEach
    void setUp() throws Exception {
        hospitalReferralRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        createConsentedUser("uid-referral-a", "referral-a@example.com", true, Role.MOTHER);
        createConsentedUser("uid-referral-b", "referral-b@example.com", true, Role.MOTHER);
        createConsentedUser("uid-referral-unverified", "referral-unverified@example.com", false, Role.MOTHER);
        createConsentedUser("uid-referral-admin", "referral-admin@example.com", true, Role.SUPER_ADMIN);

        when(firebaseTokenVerifier.verify(TOKEN_A))
                .thenReturn(new VerifiedFirebaseToken("uid-referral-a", "referral-a@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_B))
                .thenReturn(new VerifiedFirebaseToken("uid-referral-b", "referral-b@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_UNVERIFIED))
                .thenReturn(new VerifiedFirebaseToken("uid-referral-unverified", "referral-unverified@example.com", false, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-referral-admin", "referral-admin@example.com", true, null, null));

        hospitalId = hospitalPartnerRepository.findAll().get(0).getId();
    }

    private void createConsentedUser(String uid, String email, boolean verified, Role role) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(verified);
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

    private JsonNode createReferral(String token, String note) throws Exception {
        String body = mockMvc.perform(post("/api/v1/me/hospital-referrals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("hospitalId", hospitalId, "note", note))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void create_returnsCodeAndHospitalName_andIsIdempotentWhileOpen() throws Exception {
        String longNote = "Tour request: Priya, 9999999999, second trimester. ".repeat(4);
        JsonNode first = createReferral(TOKEN_A, longNote);

        assertTrue(first.get("code").asText().matches("NB-[A-HJ-NP-Z2-9]{8}"), first.get("code").asText());
        assertEquals("REQUESTED", first.get("status").asText());
        assertEquals(hospitalPartnerRepository.findById(hospitalId).get().getName(), first.get("hospitalName").asText());
        assertEquals(longNote, first.get("note").asText());
        assertTrue(first.get("userId") == null, "userId is admin-only");

        JsonNode second = createReferral(TOKEN_A, "again");
        assertEquals(first.get("code").asText(), second.get("code").asText());
        assertEquals(1, hospitalReferralRepository.count());
    }

    @Test
    void list_showsOnlyOwnReferrals() throws Exception {
        createReferral(TOKEN_A, "a");

        mockMvc.perform(get("/api/v1/me/hospital-referrals").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
        mockMvc.perform(get("/api/v1/me/hospital-referrals").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void unknownHospital_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/me/hospital-referrals")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hospitalId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOSPITAL_NOT_FOUND"));
    }

    @Test
    void unverifiedUser_cannotCreate() throws Exception {
        mockMvc.perform(post("/api/v1/me/hospital-referrals")
                        .header("Authorization", "Bearer " + TOKEN_UNVERIFIED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hospitalId\":" + hospitalId + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void admin_listsAndConfirms_idempotently_thenUserCanReferAgain() throws Exception {
        JsonNode referral = createReferral(TOKEN_A, "a");
        long id = referral.get("id").asLong();

        mockMvc.perform(get("/api/v1/admin/hospital-referrals").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/hospital-referrals").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").exists());

        String confirmedAt = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/hospital-referrals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString()).get("confirmedAt").asText();

        mockMvc.perform(post("/api/v1/admin/hospital-referrals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedAt").value(confirmedAt));

        mockMvc.perform(post("/api/v1/admin/hospital-referrals/999999/confirm")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOSPITAL_REFERRAL_NOT_FOUND"));

        // Once the earlier one is confirmed, a new request to the same hospital is a fresh referral.
        JsonNode next = createReferral(TOKEN_A, "second visit");
        assertNotEquals(referral.get("code").asText(), next.get("code").asText());
        assertEquals(2, hospitalReferralRepository.count());
    }
}
