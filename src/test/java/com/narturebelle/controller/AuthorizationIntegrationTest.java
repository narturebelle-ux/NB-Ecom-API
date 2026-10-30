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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the 4 scenarios required by the AUTH CONTRACT: no token -> 401, unverified user -> 403
 * EMAIL_NOT_VERIFIED, cross-user access -> 403 FORBIDDEN, admin bypass -> 200.
 * FirebaseTokenVerifier is mocked so no real Firebase credentials are needed to run this.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationIntegrationTest {

    private static final String USER_A_TOKEN = "token-user-a";
    private static final String UNVERIFIED_TOKEN = "token-unverified-user-a";
    private static final String ADMIN_TOKEN = "token-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long userBId;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();

        User userA = new User();
        userA.setFirebaseUid("uid-a");
        userA.setEmail("a@example.com");
        userA.setEmailVerified(true);
        userA.setRoles(Set.of(Role.MOTHER));
        userRepository.save(userA);

        User userB = new User();
        userB.setFirebaseUid("uid-b");
        userB.setEmail("b@example.com");
        userB.setEmailVerified(true);
        userB.setRoles(Set.of(Role.MOTHER));
        userBId = userRepository.save(userB).getId();

        User admin = new User();
        admin.setFirebaseUid("uid-admin");
        admin.setEmail("admin@example.com");
        admin.setEmailVerified(true);
        admin.setRoles(Set.of(Role.SUPER_ADMIN));
        userRepository.save(admin);

        when(firebaseTokenVerifier.verify(USER_A_TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-a", "a@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(UNVERIFIED_TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-a", "a@example.com", false, null, null));
        when(firebaseTokenVerifier.verify(ADMIN_TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-admin", "admin@example.com", true, null, null));
    }

    @Test
    void noToken_returns401Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unverifiedUser_updatingProfile_returns403EmailNotVerified() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + UNVERIFIED_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void userAccessingAnotherUsersData_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userBId)
                        .header("Authorization", "Bearer " + USER_A_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminAccessingAnyUsersData_works() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userBId)
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("b@example.com"));
    }
}
