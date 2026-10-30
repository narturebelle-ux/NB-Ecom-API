package com.narturebelle.controller;

import com.narturebelle.repository.AuditEventRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 0.3: /auth/firebase audits USER_CREATED only on actual creation, never on a returning login. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        auditEventRepository.deleteAll();
    }

    @Test
    void userCreated_auditsOnce_notAgainOnReturningLogin() throws Exception {
        when(firebaseTokenVerifier.verify("audit-token"))
                .thenReturn(new VerifiedFirebaseToken("uid-audit", "audit@example.com", true, null, null));

        mockMvc.perform(post("/api/v1/auth/firebase").header("Authorization", "Bearer audit-token"))
                .andExpect(status().isOk());

        assertThat(auditEventRepository.findAll())
                .hasSize(1)
                .allSatisfy(event -> assertThat(event.getAction()).isEqualTo("USER_CREATED"));

        // Same user logs in again - must not create a second audit row.
        mockMvc.perform(post("/api/v1/auth/firebase").header("Authorization", "Bearer audit-token"))
                .andExpect(status().isOk());

        assertThat(auditEventRepository.findAll()).hasSize(1);
    }
}
