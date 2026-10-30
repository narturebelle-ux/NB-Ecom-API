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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 0.2: new signups default to MOTHER, and /api/v1/admin/** accepts any *_ADMIN role, not just SUPER_ADMIN. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleMappingTest {

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
    void newSignup_defaultsToMotherRole() throws Exception {
        when(firebaseTokenVerifier.verify("new-user-token"))
                .thenReturn(new VerifiedFirebaseToken("uid-new", "new@example.com", true, null, null));

        mockMvc.perform(post("/api/v1/auth/firebase").header("Authorization", "Bearer new-user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("MOTHER"))
                .andExpect(jsonPath("$.role").value("MOTHER"));
    }

    @Test
    void nonAdminRole_blockedFromAdminNamespace() throws Exception {
        User mother = new User();
        mother.setFirebaseUid("uid-mother");
        mother.setEmail("mother@example.com");
        mother.setEmailVerified(true);
        mother.setRoles(Set.of(Role.MOTHER));
        userRepository.save(mother);

        when(firebaseTokenVerifier.verify("mother-token"))
                .thenReturn(new VerifiedFirebaseToken("uid-mother", "mother@example.com", true, null, null));

        mockMvc.perform(get("/api/v1/admin/anything").header("Authorization", "Bearer mother-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anyAdminRole_notJustSuperAdmin_passesTheAdminGate() throws Exception {
        // CLINIC_ADMIN specifically, to prove the gate isn't hardcoded to SUPER_ADMIN only.
        User clinicAdmin = new User();
        clinicAdmin.setFirebaseUid("uid-clinic-admin");
        clinicAdmin.setEmail("clinicadmin@example.com");
        clinicAdmin.setEmailVerified(true);
        clinicAdmin.setRoles(Set.of(Role.CLINIC_ADMIN));
        userRepository.save(clinicAdmin);

        when(firebaseTokenVerifier.verify("clinic-admin-token"))
                .thenReturn(new VerifiedFirebaseToken("uid-clinic-admin", "clinicadmin@example.com", true, null, null));

        // No controller is mapped under /api/v1/admin/** yet, so passing the authorization gate
        // surfaces as 404 (no handler), not 403 (blocked) - that distinction is exactly what proves
        // the role check itself let the request through.
        mockMvc.perform(get("/api/v1/admin/anything").header("Authorization", "Bearer clinic-admin-token"))
                .andExpect(status().isNotFound());
    }
}
