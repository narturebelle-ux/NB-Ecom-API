package com.narturebelle.controller;

import com.narturebelle.model.Role;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 0.6: /api/v1/me (new) and /api/v1/users/me (deprecated alias) stay in sync, same rules. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeControllerTest {

    private static final String TOKEN = "me-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();

        User user = new User();
        user.setFirebaseUid("uid-me");
        user.setEmail("me@example.com");
        user.setEmailVerified(true);
        user.setFirstName("Original");
        user.setRoles(Set.of(Role.MOTHER));
        userRepository.save(user);

        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-me", "me@example.com", true, null, null));
    }

    @Test
    void getMe_includesPendingConsentsAndHasActiveJourneyPlaceholders() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Original"))
                .andExpect(jsonPath("$.pendingConsents").isArray())
                .andExpect(jsonPath("$.hasActiveJourney").value(false));
    }

    @Test
    void patchMe_updatesProfile() throws Exception {
        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));
    }

    @Test
    void deprecatedUsersMeAlias_stillWorks_sameData() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Original"));

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\":\"ViaOldAlias\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("ViaOldAlias"));

        // Confirm the update via the OLD endpoint is visible through the NEW one too - same data.
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("ViaOldAlias"));
    }
}
