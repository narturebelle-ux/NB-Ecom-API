package com.narturebelle.controller;

import com.narturebelle.model.CommunityEvent;
import com.narturebelle.model.CommunityEventStatus;
import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.Modality;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.CommunityEventRepository;
import com.narturebelle.repository.CommunityEventRsvpRepository;
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

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers Joy Circle events + RSVPs (Phase 6). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JoyCircleTest {

    private static final String TOKEN_A = "joy-token-a";
    private static final String TOKEN_B = "joy-token-b";
    private static final String TOKEN_ADMIN = "joy-token-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private CommunityEventRepository communityEventRepository;
    @Autowired private CommunityEventRsvpRepository communityEventRsvpRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() throws Exception {
        communityEventRsvpRepository.deleteAll();
        communityEventRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        createConsentedUser("uid-joy-a", "joy-a@example.com", Role.MOTHER);
        createConsentedUser("uid-joy-b", "joy-b@example.com", Role.MOTHER);
        createConsentedUser("uid-joy-admin", "joy-admin@example.com", Role.SUPER_ADMIN);

        when(firebaseTokenVerifier.verify(TOKEN_A))
                .thenReturn(new VerifiedFirebaseToken("uid-joy-a", "joy-a@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_B))
                .thenReturn(new VerifiedFirebaseToken("uid-joy-b", "joy-b@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-joy-admin", "joy-admin@example.com", true, null, null));
    }

    private void createConsentedUser(String uid, String email, Role role) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(role));
        Long id = userRepository.save(user).getId();
        for (ConsentDocumentType type : ConsentDocumentType.values()) {
            Consent consent = new Consent();
            consent.setUserId(id);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }
    }

    private CommunityEvent event(LocalDateTime startsAt, Integer capacity, CommunityEventStatus status) {
        CommunityEvent event = new CommunityEvent();
        event.setTitle("Circle");
        event.setHostName("Host");
        event.setStartsAt(startsAt);
        event.setEndsAt(startsAt.plusHours(1));
        event.setModality(Modality.VIRTUAL);
        event.setJoinUrl("https://join.example/secret");
        event.setCapacity(capacity);
        event.setStatus(status);
        return communityEventRepository.save(event);
    }

    @Test
    void publicList_needsNoToken_hidesJoinUrl_andExcludesPastByDefault() throws Exception {
        event(LocalDateTime.now().plusDays(1), 10, CommunityEventStatus.SCHEDULED);
        event(LocalDateTime.now().minusDays(2), 10, CommunityEventStatus.SCHEDULED);

        mockMvc.perform(get("/api/v1/joy-circle/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].joinUrl").doesNotExist())
                .andExpect(jsonPath("$[0].spotsLeft").value(10))
                .andExpect(jsonPath("$[0].disclaimer").exists());

        mockMvc.perform(get("/api/v1/joy-circle/events").param("includePast", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void rsvp_withoutToken_returns401() throws Exception {
        CommunityEvent e = event(LocalDateTime.now().plusDays(1), 10, CommunityEventStatus.SCHEDULED);
        mockMvc.perform(post("/api/v1/joy-circle/events/" + e.getId() + "/rsvp"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rsvp_returnsJoinUrlAndUpdatedCount_isIdempotent_andCanBeCancelled() throws Exception {
        CommunityEvent e = event(LocalDateTime.now().plusDays(1), 10, CommunityEventStatus.SCHEDULED);

        mockMvc.perform(post("/api/v1/joy-circle/events/" + e.getId() + "/rsvp").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinUrl").value("https://join.example/secret"))
                .andExpect(jsonPath("$.event.rsvpCount").value(1))
                .andExpect(jsonPath("$.event.spotsLeft").value(9));

        mockMvc.perform(post("/api/v1/joy-circle/events/" + e.getId() + "/rsvp").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.rsvpCount").value(1));
        assertEquals(1, communityEventRsvpRepository.count());

        mockMvc.perform(get("/api/v1/me/joy-circle/rsvps").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].eventId").value(e.getId()));
        mockMvc.perform(get("/api/v1/me/joy-circle/rsvps").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        mockMvc.perform(delete("/api/v1/joy-circle/events/" + e.getId() + "/rsvp").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/joy-circle/events/" + e.getId() + "/rsvp").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNoContent());
        assertEquals(0, communityEventRsvpRepository.count());
    }

    @Test
    void rsvp_fullEvent_returns409EventFull() throws Exception {
        CommunityEvent e = event(LocalDateTime.now().plusDays(1), 1, CommunityEventStatus.SCHEDULED);

        mockMvc.perform(post("/api/v1/joy-circle/events/" + e.getId() + "/rsvp").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.spotsLeft").value(0));
        mockMvc.perform(post("/api/v1/joy-circle/events/" + e.getId() + "/rsvp").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_FULL"));
    }

    @Test
    void rsvp_cancelledOrStartedEvent_returns409EventNotOpen() throws Exception {
        CommunityEvent cancelled = event(LocalDateTime.now().plusDays(1), null, CommunityEventStatus.CANCELLED);
        CommunityEvent started = event(LocalDateTime.now().minusMinutes(10), null, CommunityEventStatus.SCHEDULED);

        mockMvc.perform(post("/api/v1/joy-circle/events/" + cancelled.getId() + "/rsvp").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_OPEN"));
        mockMvc.perform(post("/api/v1/joy-circle/events/" + started.getId() + "/rsvp").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_OPEN"));
        mockMvc.perform(post("/api/v1/joy-circle/events/999999/rsvp").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void admin_createsAndCancelsEvents_nonAdminForbidden() throws Exception {
        String startsAt = LocalDateTime.now().plusDays(2).withNano(0).toString();
        String endsAt = LocalDateTime.now().plusDays(2).plusHours(1).withNano(0).toString();
        String body = "{\"title\":\"Circle\",\"hostName\":\"Host\",\"modality\":\"VIRTUAL\",\"capacity\":5,"
                + "\"startsAt\":\"" + startsAt + "\",\"endsAt\":\"" + endsAt + "\"}";

        mockMvc.perform(post("/api/v1/admin/joy-circle/events").header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        String created = mockMvc.perform(post("/api/v1/admin/joy-circle/events").header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.spotsLeft").value(5))
                .andReturn().getResponse().getContentAsString();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/joy-circle/events/" + id + "/cancel").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        String backwards = "{\"title\":\"Circle\",\"hostName\":\"Host\",\"modality\":\"VIRTUAL\","
                + "\"startsAt\":\"" + endsAt + "\",\"endsAt\":\"" + startsAt + "\"}";
        mockMvc.perform(post("/api/v1/admin/joy-circle/events").header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(backwards))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.endsAt").exists());
    }
}
