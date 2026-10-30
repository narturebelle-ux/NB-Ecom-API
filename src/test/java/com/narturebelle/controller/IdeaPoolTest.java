package com.narturebelle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.email.EmailMessage;
import com.narturebelle.email.EmailSender;
import com.narturebelle.model.*;
import com.narturebelle.repository.*;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Covers the idea pool: submitting, paid-vote counting toward the goal, the pool flag, and Idea Impact. */
@SpringBootTest(properties = "ideas.vote-goal=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdeaPoolTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private IdeaRepository ideaRepository;
    @Autowired private IdeaVoteRepository ideaVoteRepository;
    @Autowired private FeatureFlagRepository featureFlagRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private EmailSender emailSender;

    @BeforeEach
    void setUp() throws Exception {
        ideaVoteRepository.deleteAll();
        ideaRepository.deleteAll();
        orderRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();
        setPoolOpen(true);

        member("author", "Asha", "Rao", false);
        member("free", "Divya", null, false);
        member("paid1", "Meera", "K", true);
        member("paid2", "Priya", "S", true);
        member("admin", "Ops", null, false, Role.OPERATIONS_ADMIN);
    }

    @AfterEach
    void reopenPool() {
        setPoolOpen(true);
    }

    private void setPoolOpen(boolean open) {
        FeatureFlag flag = featureFlagRepository.findById("ideaPool").orElseGet(FeatureFlag::new);
        flag.setKey("ideaPool");
        flag.setEnabled(open);
        featureFlagRepository.save(flag);
    }

    private void member(String name, String first, String last, boolean paid) throws Exception {
        member(name, first, last, paid, Role.MOTHER);
    }

    private void member(String name, String first, String last, boolean paid, Role role) throws Exception {
        User user = new User();
        user.setFirebaseUid("uid-idea-" + name);
        user.setEmail(name + "@example.com");
        user.setEmailVerified(true);
        user.setFirstName(first);
        user.setLastName(last);
        user.setRoles(Set.of(role));
        Long id = userRepository.save(user).getId();
        for (ConsentDocumentType type : Set.of(ConsentDocumentType.TERMS, ConsentDocumentType.PRIVACY)) {
            Consent consent = new Consent();
            consent.setUserId(id);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }
        if (paid) {
            Order order = new Order();
            order.setUserId(id);
            order.setPlanVersionId(1L);
            order.setIdempotencyKey("idea-" + name);
            order.setRequestFingerprint("fp");
            order.setStatus(OrderStatus.PAID);
            order.setPaymentMode(PaymentMode.ONE_TIME);
            order.setPrice(new BigDecimal("9999"));
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        }
        when(firebaseTokenVerifier.verify("tok-" + name))
                .thenReturn(new VerifiedFirebaseToken("uid-idea-" + name, name + "@example.com", true, null, null));
    }

    private static String bearer(String name) {
        return "Bearer tok-" + name;
    }

    private long submit(String title, boolean featureConsent) throws Exception {
        String body = mockMvc.perform(post("/api/v1/me/ideas").header("Authorization", bearer("author"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"NUTRITION_AND_WELLNESS\",\"title\":\"" + title + "\","
                                + "\"description\":\"Weekly regional recipes for each trimester\",\"featureConsent\":" + featureConsent + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(org.hamcrest.Matchers.startsWith("IDEA-")))
                .andExpect(jsonPath("$.authorName").value("Asha R."))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void onlyPaidVotesCountTowardTheGoal_andReachingItEmailsTheTeam() throws Exception {
        long id = submit("Trimester recipes", false);

        mockMvc.perform(get("/api/v1/ideas/meta"))
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.voteGoal").value(2))
                .andExpect(jsonPath("$.categories.length()").value(5));

        mockMvc.perform(post("/api/v1/ideas/" + id + "/vote").header("Authorization", bearer("author")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEA_SELF_VOTE"));
        mockMvc.perform(post("/api/v1/ideas/" + id + "/vote"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/ideas/" + id + "/vote").header("Authorization", bearer("free")))
                .andExpect(jsonPath("$.votes").value(1)).andExpect(jsonPath("$.paidVotes").value(0))
                .andExpect(jsonPath("$.votedByMe").value(true));
        mockMvc.perform(post("/api/v1/ideas/" + id + "/vote").header("Authorization", bearer("paid1")))
                .andExpect(jsonPath("$.paidVotes").value(1)).andExpect(jsonPath("$.goalReached").value(false));
        // Voting twice keeps one vote.
        mockMvc.perform(post("/api/v1/ideas/" + id + "/vote").header("Authorization", bearer("paid1")))
                .andExpect(jsonPath("$.votes").value(2));
        mockMvc.perform(post("/api/v1/ideas/" + id + "/vote").header("Authorization", bearer("paid2")))
                .andExpect(jsonPath("$.paidVotes").value(2)).andExpect(jsonPath("$.goalReached").value(true));

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(2)).send(sent.capture()); // new idea + goal reached
        assertTrue(sent.getAllValues().get(1).subject().contains("reached 2 paid votes"));
        assertNotNull(ideaRepository.findById(id).orElseThrow().getGoalReachedAt());

        // Unvoting removes the vote.
        mockMvc.perform(delete("/api/v1/ideas/" + id + "/vote").header("Authorization", bearer("free")))
                .andExpect(jsonPath("$.votes").value(2)).andExpect(jsonPath("$.votedByMe").value(false));

        // Public pool (no token): sorted by paid votes, no viewer fields.
        String other = String.valueOf(submit("Night-time helpline", false));
        mockMvc.perform(get("/api/v1/ideas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[1].id").value(Long.parseLong(other)))
                .andExpect(jsonPath("$[0].votedByMe").doesNotExist());
        mockMvc.perform(get("/api/v1/ideas?category=COMMUNITY")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/ideas/" + id)).andExpect(jsonPath("$.paidVotes").value(2));
    }

    @Test
    void closingThePool_stopsSubmissionsAndVotes_butKeepsBrowsing() throws Exception {
        long id = submit("Partner classes", false);
        setPoolOpen(false);

        mockMvc.perform(post("/api/v1/me/ideas").header("Authorization", bearer("author"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"COMMUNITY\",\"title\":\"x\",\"description\":\"y\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEA_POOL_CLOSED"));
        mockMvc.perform(post("/api/v1/ideas/" + id + "/vote").header("Authorization", bearer("paid1")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEA_POOL_CLOSED"));
        mockMvc.perform(get("/api/v1/ideas")).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/ideas/meta")).andExpect(jsonPath("$.open").value(false));
    }

    @Test
    void implementedIdeas_featureOnIdeaImpact_namedOnlyWithConsent() throws Exception {
        long named = submit("Lullaby library", true);
        long anonymous = submit("Hospital bag checklist", false);

        mockMvc.perform(post("/api/v1/admin/ideas/" + named + "/status").header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IMPLEMENTED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMPACT_NOTE_REQUIRED"));
        for (long id : new long[]{named, anonymous}) {
            mockMvc.perform(post("/api/v1/admin/ideas/" + id + "/status").header("Authorization", bearer("admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"IMPLEMENTED\",\"impactNote\":\"Live in the app since this week\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IMPLEMENTED"));
        }

        mockMvc.perform(get("/api/v1/ideas/impact"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == " + named + ")].authorName").value("Asha R."))
                .andExpect(jsonPath("$[?(@.id == " + anonymous + ")].authorName").value("A NartureBelle mom"))
                .andExpect(jsonPath("$[0].impactNote").value("Live in the app since this week"));

        // Voting has finished on an implemented idea; declined ideas leave the pool.
        mockMvc.perform(post("/api/v1/ideas/" + named + "/vote").header("Authorization", bearer("paid1")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEA_NOT_OPEN"));
        mockMvc.perform(post("/api/v1/admin/ideas/" + anonymous + "/status").header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DECLINED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ideas/" + anonymous)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/me/ideas").header("Authorization", bearer("author")))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == " + named + ")].featureConsent").value(true));

        mockMvc.perform(get("/api/v1/admin/ideas").header("Authorization", bearer("free")))
                .andExpect(status().isForbidden());
        verify(emailSender, atLeastOnce()).send(any());
    }
}
