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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 0.5: PATCH /api/v1/admin/feature-flags/{key} - admin-only, reason required, audited. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeatureFlagAdminTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();

        User mother = new User();
        mother.setFirebaseUid("uid-mother");
        mother.setEmail("mother@example.com");
        mother.setEmailVerified(true);
        mother.setRoles(Set.of(Role.MOTHER));
        userRepository.save(mother);

        User admin = new User();
        admin.setFirebaseUid("uid-admin");
        admin.setEmail("admin@example.com");
        admin.setEmailVerified(true);
        admin.setRoles(Set.of(Role.OPERATIONS_ADMIN));
        userRepository.save(admin);

        when(firebaseTokenVerifier.verify("mother-token"))
                .thenReturn(new VerifiedFirebaseToken("uid-mother", "mother@example.com", true, null, null));
        when(firebaseTokenVerifier.verify("admin-token"))
                .thenReturn(new VerifiedFirebaseToken("uid-admin", "admin@example.com", true, null, null));
    }

    @Test
    void nonAdmin_cannotPatchFlags() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/feature-flags/babyCareProducts")
                        .header("Authorization", "Bearer mother-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"reason\":\"test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void admin_missingReason_returnsValidationFailed() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/feature-flags/babyCareProducts")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.reason").exists());
    }

    @Test
    void admin_validPatch_updatesFlagAndReflectsInGetFeatures() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/feature-flags/babyCareProducts")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"reason\":\"QA verification\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.babyCareProducts").value(true))
                .andExpect(jsonPath("$.nurseCompanion").value(true));
    }

    @Test
    void unknownKey_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/feature-flags/doesNotExist")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"reason\":\"test\"}"))
                .andExpect(status().isNotFound());
    }
}
