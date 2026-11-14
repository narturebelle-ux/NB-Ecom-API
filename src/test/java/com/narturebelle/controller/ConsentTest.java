package com.narturebelle.controller;

import com.narturebelle.mail.MailsClient;
import com.narturebelle.mail.OutgoingMail;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 1.1: consent documents + grant/revoke, and how pendingConsents on /api/v1/me reacts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsentTest {

    private static final String TOKEN = "consent-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @MockBean
    private MailsClient mailsClient;

    @BeforeEach
    void setUp() throws Exception {
        consentRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFirebaseUid("uid-consent");
        user.setEmail("consent@example.com");
        user.setEmailVerified(true);
        user.setFirstName("Consent");
        user.setRoles(Set.of(Role.MOTHER));
        userRepository.save(user);

        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-consent", "consent@example.com", true, null, null));
    }

    @Test
    void currentDocuments_arePublic_noAuthNeeded() throws Exception {
        mockMvc.perform(get("/api/v1/consent-documents/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").exists());
    }

    @Test
    void newUser_hasBothDocumentsPending() throws Exception {
        mockMvc.perform(get("/api/v1/me/consents").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending", org.hamcrest.Matchers.containsInAnyOrder("PRIVACY", "TERMS")))
                .andExpect(jsonPath("$.history").isArray());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingConsents", org.hamcrest.Matchers.containsInAnyOrder("PRIVACY", "TERMS")));
    }

    @Test
    void grant_removesTypeFromPending_andIsAudited() throws Exception {
        mockMvc.perform(post("/api/v1/me/consents")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"TERMS\",\"documentVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("TERMS"));

        mockMvc.perform(get("/api/v1/me/consents").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending", org.hamcrest.Matchers.contains("PRIVACY")));
    }

    @Test
    void firstTermsAndPrivacy_sendTheSignupWelcomeOnce_andARegrantSendsNoMore() throws Exception {
        grant("TERMS");
        verify(mailsClient, never()).send(any());
        grant("PRIVACY");

        ArgumentCaptor<OutgoingMail> sent = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(mailsClient, times(1)).send(sent.capture());
        assertEquals("MOTHER_SIGNUP_WELCOME", sent.getValue().eventType());
        assertEquals("welcome-signup-" + sent.getValue().userId(), sent.getValue().eventId());

        mockMvc.perform(post("/api/v1/me/consents/PRIVACY/revoke").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
        grant("PRIVACY");
        verify(mailsClient, times(1)).send(any());
    }

    @Test
    void staffAccounts_getNoSignupWelcome() throws Exception {
        User user = userRepository.findByFirebaseUid("uid-consent").orElseThrow();
        user.setRoles(new java.util.HashSet<>(Set.of(Role.MOTHER, Role.OPERATIONS_ADMIN)));
        userRepository.save(user);

        grant("TERMS");
        grant("PRIVACY");
        verify(mailsClient, never()).send(any());
    }

    private void grant(String type) throws Exception {
        mockMvc.perform(post("/api/v1/me/consents")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"" + type + "\",\"documentVersion\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void grant_withStaleVersion_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/me/consents")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"TERMS\",\"documentVersion\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_VERSION_OUTDATED"));
    }

    @Test
    void revoke_putsTypeBackIntoPending() throws Exception {
        mockMvc.perform(post("/api/v1/me/consents")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"PRIVACY\",\"documentVersion\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/consents/PRIVACY/revoke")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedAt").exists());

        mockMvc.perform(get("/api/v1/me/consents").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending", org.hamcrest.Matchers.containsInAnyOrder("PRIVACY", "TERMS")));
    }

    @Test
    void revoke_withNothingToRevoke_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/me/consents/TERMS/revoke")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void revoke_withUnknownType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/me/consents/NOT_A_TYPE/revoke")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
