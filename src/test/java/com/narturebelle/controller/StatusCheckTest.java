package com.narturebelle.controller;

import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F0-S5 (D44): the one public request sent after a deployment. */
@SpringBootTest(properties = "app.version=abc123")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatusCheckTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @SpyBean
    private UserRepository userRepository;

    @ParameterizedTest
    @ValueSource(strings = {"/health", "/"})
    void isPublic_andShowsOnlyTheStatusAndVersion(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\",\"version\":\"abc123\"}", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health", "/"})
    void aTokenOnTheRequest_causesNoVerificationOrUserLookup(String path) throws Exception {
        clearInvocations(firebaseTokenVerifier, userRepository); // Start-up data seeding uses the repository.

        mockMvc.perform(get(path).header("Authorization", "Bearer some-token"))
                .andExpect(status().isOk());

        verifyNoInteractions(firebaseTokenVerifier);
        verifyNoInteractions(userRepository);
    }

    @Test
    void theDatabaseCheck_staysAvailable() throws Exception {
        mockMvc.perform(get("/health/db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("ok"));
    }

    @Test
    void otherEndpoints_stillNeedSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
