package com.narturebelle.controller;

import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the three firstName/lastName derivation branches on first signup (POST /auth/firebase):
 * Google name claim, email local-part fallback, and phone-only (nothing to derive from).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupNameDerivationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void googleSignIn_usesNameClaim_splitOnWhitespace() throws Exception {
        when(firebaseTokenVerifier.verify("google-token"))
                .thenReturn(new VerifiedFirebaseToken("uid-google", "someone@gmail.com", true, null, "Priya Sharma Rao"));

        mockMvc.perform(post("/api/v1/auth/firebase").header("Authorization", "Bearer google-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Priya"))
                .andExpect(jsonPath("$.lastName").value("Sharma Rao"));
    }

    @Test
    void emailPasswordSignIn_noNameClaim_derivesFromEmailLocalPart() throws Exception {
        when(firebaseTokenVerifier.verify("email-token"))
                .thenReturn(new VerifiedFirebaseToken("uid-email", "priya.sharma@example.com", false, null, null));

        mockMvc.perform(post("/api/v1/auth/firebase").header("Authorization", "Bearer email-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Priya"))
                .andExpect(jsonPath("$.lastName").value("Sharma"));
    }

    @Test
    void phoneOnlySignIn_noEmailNoName_leavesBothNull() throws Exception {
        when(firebaseTokenVerifier.verify("phone-token"))
                .thenReturn(new VerifiedFirebaseToken("uid-phone", null, false, "+919876543210", null));

        mockMvc.perform(post("/api/v1/auth/firebase").header("Authorization", "Bearer phone-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value(nullValue()))
                .andExpect(jsonPath("$.lastName").value(nullValue()));
    }

    @Test
    void returningUser_nameNeverOverwritten_evenIfTokenNameChanges() throws Exception {
        when(firebaseTokenVerifier.verify("first-login"))
                .thenReturn(new VerifiedFirebaseToken("uid-returning", "someone@gmail.com", true, null, "Original Name"));
        mockMvc.perform(post("/api/v1/auth/firebase").header("Authorization", "Bearer first-login"))
                .andExpect(jsonPath("$.firstName").value("Original"));

        // Same user logs in again later with a different Google display name - must NOT overwrite.
        when(firebaseTokenVerifier.verify("second-login"))
                .thenReturn(new VerifiedFirebaseToken("uid-returning", "someone@gmail.com", true, null, "Changed Name"));
        mockMvc.perform(post("/api/v1/auth/firebase").header("Authorization", "Bearer second-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Original"))
                .andExpect(jsonPath("$.lastName").value("Name"));
    }

    @Test
    void existingUserWithBlankNames_getsBackfilledOnNextLogin() throws Exception {
        // Simulates a user created before name derivation existed: no firstName/lastName on file.
        User legacyUser = new User();
        legacyUser.setFirebaseUid("uid-legacy");
        legacyUser.setEmail("legacy@gmail.com");
        legacyUser.setEmailVerified(true);
        userRepository.save(legacyUser);

        when(firebaseTokenVerifier.verify("legacy-login"))
                .thenReturn(new VerifiedFirebaseToken("uid-legacy", "legacy@gmail.com", true, null, "Backfilled Name"));

        mockMvc.perform(post("/api/v1/auth/firebase").header("Authorization", "Bearer legacy-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Backfilled"))
                .andExpect(jsonPath("$.lastName").value("Name"));
    }
}
