package com.narturebelle.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.ChildRepository;
import com.narturebelle.repository.ChildVaccinationRepository;
import com.narturebelle.repository.ConsentRepository;
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

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers child profiles and the vaccination tracker (Phase 6, Workflow G). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChildTest {

    private static final String TOKEN_A = "child-token-a";
    private static final String TOKEN_B = "child-token-b";
    private static final String TOKEN_UNCONSENTED = "child-token-unconsented";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private ChildRepository childRepository;
    @Autowired private ChildVaccinationRepository childVaccinationRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() throws Exception {
        childVaccinationRepository.deleteAll();
        childRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        createConsentedUser("uid-child-a", "child-a@example.com");
        createConsentedUser("uid-child-b", "child-b@example.com");
        createUser("uid-child-unconsented", "child-unconsented@example.com");

        when(firebaseTokenVerifier.verify(TOKEN_A))
                .thenReturn(new VerifiedFirebaseToken("uid-child-a", "child-a@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_B))
                .thenReturn(new VerifiedFirebaseToken("uid-child-b", "child-b@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_UNCONSENTED))
                .thenReturn(new VerifiedFirebaseToken("uid-child-unconsented", "child-unconsented@example.com", true, null, null));
    }

    private Long createUser(String uid, String email) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        return userRepository.save(user).getId();
    }

    private Long createConsentedUser(String uid, String email) {
        Long id = createUser(uid, email);
        for (ConsentDocumentType type : ConsentDocumentType.values()) {
            Consent consent = new Consent();
            consent.setUserId(id);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }
        return id;
    }

    private long createChild(String token, LocalDate dob) throws Exception {
        String body = mockMvc.perform(post("/api/v1/me/children")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha\",\"dateOfBirth\":\"" + dob + "\",\"sex\":\"FEMALE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private JsonNode vaccinations(String token, long childId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/me/children/" + childId + "/vaccinations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode itemNamed(JsonNode response, String vaccineName) {
        for (JsonNode item : response.get("items")) {
            if (item.get("vaccineName").asText().equals(vaccineName)) {
                return item;
            }
        }
        throw new AssertionError("No schedule item named " + vaccineName);
    }

    @Test
    void unconsentedUser_returns403ConsentRequired() throws Exception {
        mockMvc.perform(get("/api/v1/me/children").header("Authorization", "Bearer " + TOKEN_UNCONSENTED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void createListAndPatch_child() throws Exception {
        long childId = createChild(TOKEN_A, LocalDate.now().minusDays(10));

        mockMvc.perform(get("/api/v1/me/children").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Asha"))
                .andExpect(jsonPath("$[0].sex").value("FEMALE"));

        mockMvc.perform(patch("/api/v1/me/children/" + childId)
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha R\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha R"))
                .andExpect(jsonPath("$.sex").value("FEMALE"));
    }

    @Test
    void futureDateOfBirth_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/me/children")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha\",\"dateOfBirth\":\"" + LocalDate.now().plusDays(3) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.dateOfBirth").exists());
    }

    @Test
    void anotherUsersChild_returns404NotForbidden() throws Exception {
        long childId = createChild(TOKEN_A, LocalDate.now().minusDays(10));

        mockMvc.perform(get("/api/v1/me/children/" + childId + "/vaccinations").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHILD_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/me/children").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void vaccinations_computeStatusFromDateOfBirth() throws Exception {
        // Born 60 days ago: birth and 6-week doses are past due, 10-week (day 70) is due within 30 days,
        // 14-week (day 98, 38 days out) is upcoming.
        long childId = createChild(TOKEN_A, LocalDate.now().minusDays(60));
        JsonNode response = vaccinations(TOKEN_A, childId);

        assertEquals("PENDING_CLINICAL_REVIEW", response.get("schedule").get("reviewStatus").asText());
        assertEquals(16, response.get("items").size());
        assertEquals("OVERDUE", itemNamed(response, "BCG").get("status").asText());
        assertEquals("OVERDUE", itemNamed(response, "PCV 1").get("status").asText());
        assertEquals("DUE", itemNamed(response, "PCV 2").get("status").asText());
        assertEquals("UPCOMING", itemNamed(response, "Rotavirus 3").get("status").asText());
        assertEquals("6 weeks", itemNamed(response, "PCV 1").get("dueAgeLabel").asText());
        assertEquals(LocalDate.now().minusDays(60).plusDays(42).toString(), itemNamed(response, "PCV 1").get("dueDate").asText());
    }

    @Test
    void recordVaccination_isIdempotentUpsert_andCanBeRemoved() throws Exception {
        LocalDate dob = LocalDate.now().minusDays(60);
        long childId = createChild(TOKEN_A, dob);
        long bcgId = itemNamed(vaccinations(TOKEN_A, childId), "BCG").get("scheduleItemId").asLong();

        mockMvc.perform(put("/api/v1/me/children/" + childId + "/vaccinations/" + bcgId)
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"givenAt\":\"" + dob + "\",\"givenWhere\":\"City Hospital\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.givenWhere").value("City Hospital"));

        mockMvc.perform(put("/api/v1/me/children/" + childId + "/vaccinations/" + bcgId)
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"givenAt\":\"" + dob.plusDays(1) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.givenAt").value(dob.plusDays(1).toString()));

        assertEquals(1, childVaccinationRepository.count());
        assertEquals("DONE", itemNamed(vaccinations(TOKEN_A, childId), "BCG").get("status").asText());

        mockMvc.perform(delete("/api/v1/me/children/" + childId + "/vaccinations/" + bcgId)
                        .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNoContent());
        // Removing again is a no-op, not a 404.
        mockMvc.perform(delete("/api/v1/me/children/" + childId + "/vaccinations/" + bcgId)
                        .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNoContent());

        assertEquals(0, childVaccinationRepository.count());
        assertEquals("OVERDUE", itemNamed(vaccinations(TOKEN_A, childId), "BCG").get("status").asText());
    }

    @Test
    void recordVaccination_beforeBirth_returns400() throws Exception {
        LocalDate dob = LocalDate.now().minusDays(60);
        long childId = createChild(TOKEN_A, dob);
        long bcgId = itemNamed(vaccinations(TOKEN_A, childId), "BCG").get("scheduleItemId").asLong();

        mockMvc.perform(put("/api/v1/me/children/" + childId + "/vaccinations/" + bcgId)
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"givenAt\":\"" + dob.minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.givenAt").exists());
    }

    @Test
    void recordVaccination_unknownScheduleItem_returns404() throws Exception {
        long childId = createChild(TOKEN_A, LocalDate.now().minusDays(60));

        mockMvc.perform(put("/api/v1/me/children/" + childId + "/vaccinations/999999")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"givenAt\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VACCINE_SCHEDULE_ITEM_NOT_FOUND"));
    }
}
