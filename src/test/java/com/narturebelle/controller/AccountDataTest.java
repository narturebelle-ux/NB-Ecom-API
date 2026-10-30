package com.narturebelle.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.model.Child;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.AccountDeletionRequestRepository;
import com.narturebelle.repository.ChildRepository;
import com.narturebelle.repository.ChildVaccinationRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers self-service data export and account-deletion requests (Phase 6). Users here have no consents on
 * purpose: both features must work for someone who has revoked consent. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDataTest {

    private static final String TOKEN_A = "account-token-a";
    private static final String TOKEN_B = "account-token-b";
    private static final String TOKEN_ADMIN = "account-token-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ChildRepository childRepository;
    @Autowired private ChildVaccinationRepository childVaccinationRepository;
    @Autowired private AccountDeletionRequestRepository accountDeletionRequestRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long userA;
    private Long userB;

    @BeforeEach
    void setUp() throws Exception {
        accountDeletionRequestRepository.deleteAll();
        childVaccinationRepository.deleteAll();
        childRepository.deleteAll();
        userRepository.deleteAll();

        userA = createUser("uid-account-a", "account-a@example.com", Role.MOTHER);
        userB = createUser("uid-account-b", "account-b@example.com", Role.MOTHER);
        createUser("uid-account-admin", "account-admin@example.com", Role.SUPER_ADMIN);

        when(firebaseTokenVerifier.verify(TOKEN_A))
                .thenReturn(new VerifiedFirebaseToken("uid-account-a", "account-a@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_B))
                .thenReturn(new VerifiedFirebaseToken("uid-account-b", "account-b@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-account-admin", "account-admin@example.com", true, null, null));
    }

    private Long createUser(String uid, String email, Role role) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(role));
        return userRepository.save(user).getId();
    }

    private void addChild(Long userId, String name) {
        Child child = new Child();
        child.setUserId(userId);
        child.setName(name);
        child.setDateOfBirth(LocalDate.now().minusDays(30));
        childRepository.save(child);
    }

    @Test
    void export_isAnAttachmentWithOnlyTheCallersData() throws Exception {
        addChild(userA, "Asha");
        addChild(userB, "Someone else");

        MvcResult result = mockMvc.perform(get("/api/v1/me/export").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(result.getResponse().getHeader("Content-Disposition").startsWith("attachment; filename=\"narturebelle-export-"));
        JsonNode export = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals("account-a@example.com", export.get("profile").get("email").asText());
        assertEquals(1, export.get("children").size());
        assertEquals("Asha", export.get("children").get(0).get("name").asText());
        assertTrue(export.has("healthRecords"));
        assertTrue(export.has("orders"));
    }

    @Test
    void deletionRequest_lifecycle_requestDuplicateCancelAndRerequest() throws Exception {
        mockMvc.perform(get("/api/v1/me/deletion-request").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELETION_REQUEST_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/me/deletion-request").header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"delete\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.confirm").exists());

        mockMvc.perform(post("/api/v1/me/deletion-request").header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"DELETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.userId").doesNotExist());

        mockMvc.perform(post("/api/v1/me/deletion-request").header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"DELETE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETION_ALREADY_REQUESTED"));

        mockMvc.perform(delete("/api/v1/me/deletion-request").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/me/deletion-request").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/me/deletion-request").header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"DELETE\"}"))
                .andExpect(status().isOk());
        assertEquals(2, accountDeletionRequestRepository.count());
    }

    @Test
    void admin_movesRequestAlong_userCannotCancelOnceInProgress() throws Exception {
        String created = mockMvc.perform(post("/api/v1/me/deletion-request").header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"DELETE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/v1/admin/deletion-requests").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/deletion-requests").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userA));

        mockMvc.perform(patch("/api/v1/admin/deletion-requests/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DELETION_STATUS_TRANSITION"));

        mockMvc.perform(patch("/api/v1/admin/deletion-requests/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(delete("/api/v1/me/deletion-request").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETION_NOT_CANCELLABLE"));

        mockMvc.perform(patch("/api/v1/admin/deletion-requests/" + id).header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
