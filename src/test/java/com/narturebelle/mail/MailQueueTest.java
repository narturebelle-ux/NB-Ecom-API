package com.narturebelle.mail;

import com.narturebelle.internal.InternalTokenFilter;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MailQueueTest {

    @Autowired private MailQueue mailQueue;
    @Autowired private OutgoingEmailDispatcher dispatcher;
    @Autowired private OutgoingEmailRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    @MockBean private MailsClient mailsClient;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    /** Makes rows due again without changing which was tried least recently. */
    private void ageRows() {
        jdbcTemplate.update("update outgoing_emails set created_at = dateadd('MINUTE', -5, created_at), last_attempt_at = dateadd('MINUTE', -5, last_attempt_at)");
    }

    private void makeDue() {
        jdbcTemplate.update("update outgoing_emails set created_at = dateadd('MINUTE', -5, created_at), last_attempt_at = null");
    }

    @Test
    void rollback_sendsNothing_andLeavesNoRow() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            mailQueue.sendToAddress("TEST", "x@example.com", null, "Subject", "Text");
            throw new IllegalStateException("the action failed");
        }));
        verifyNoInteractions(mailsClient);
        assertEquals(0, repository.count());
    }

    @Test
    void nothingIsSent_untilTheCallerCommits() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            mailQueue.sendToAddress("TEST", "x@example.com", null, "Subject", "Text");
            verifyNoInteractions(mailsClient);
        });
        verify(mailsClient).send(any());
    }

    @Test
    void mailsDown_doesNotFailTheCaller_andTheRowStaysForRetry() {
        when(mailsClient.send(any())).thenThrow(new RuntimeException("sleeping"));
        assertDoesNotThrow(() -> mailQueue.sendToAddress("TEST", "x@example.com", "r@example.com", "Subject", "Text"));

        OutgoingEmail row = repository.findAll().get(0);
        assertEquals(OutgoingEmail.Status.PENDING, row.getStatus());
        assertEquals(1, row.getAttempts());
        assertEquals("x@example.com", row.getToAddress());
    }

    @Test
    void retry_usesTheSameEventId_andClearsPersonalDataOnceSent() {
        ArgumentCaptor<OutgoingMail> sent = ArgumentCaptor.forClass(OutgoingMail.class);
        when(mailsClient.send(sent.capture())).thenThrow(new RuntimeException("sleeping")).thenReturn(true);
        mailQueue.sendToAddress("TEST", "x@example.com", "r@example.com", "Subject", "Text");

        makeDue();
        assertEquals(1, dispatcher.retryDue());

        assertEquals(sent.getAllValues().get(0).eventId(), sent.getAllValues().get(1).eventId());
        OutgoingEmail row = repository.findAll().get(0);
        assertEquals(OutgoingEmail.Status.SENT, row.getStatus());
        assertNull(row.getToAddress());
        assertNull(row.getReplyTo());
        assertNull(row.getSubject());
        assertNull(row.getBody());
        assertNotNull(row.getSentAt());
    }

    @Test
    void retry_skipsRowsThatAreSentOrTooNew() {
        when(mailsClient.send(any())).thenReturn(true);
        mailQueue.sendToAddress("TEST", "x@example.com", null, "Subject", "Text");
        makeDue();
        assertEquals(0, dispatcher.retryDue()); // already sent

        when(mailsClient.send(any())).thenThrow(new RuntimeException("sleeping"));
        mailQueue.sendToAddress("TEST", "y@example.com", null, "Subject", "Text");
        assertEquals(0, dispatcher.retryDue()); // tried a moment ago
    }

    @Test
    void afterTooManyAttempts_theRowIsMarkedFailed() {
        when(mailsClient.send(any())).thenThrow(new RuntimeException("down"));
        mailQueue.sendToAddress("TEST", "x@example.com", null, "Subject", "Text");
        Long id = repository.findAll().get(0).getId();
        for (int i = 1; i < OutgoingEmailDispatcher.MAX_ATTEMPTS; i++) {
            dispatcher.dispatch(id);
        }
        assertEquals(OutgoingEmail.Status.FAILED, repository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void rowsThatKeepFailing_dontStarveANewerRow() {
        org.mockito.Mockito.when(mailsClient.send(any())).thenAnswer(call -> {
            OutgoingMail mail = call.getArgument(0);
            if (mail.to().startsWith("bad")) {
                throw new RuntimeException("rejected");
            }
            return true;
        });
        for (int i = 0; i < 55; i++) {
            mailQueue.sendToAddress("TEST", "bad" + i + "@example.com", null, "Subject", "Text");
        }
        OutgoingEmail good = new OutgoingEmail();
        good.setEventId("care-good");
        good.setEventType("TEST");
        good.setKind(OutgoingEmail.Kind.EMAIL);
        good.setToAddress("good@example.com");
        good.setSubject("Subject");
        good.setBody("Text");
        Long goodId = repository.save(good).getId();
        ageRows();

        assertEquals(50, dispatcher.retryDue());
        assertEquals(OutgoingEmail.Status.SENT, repository.findById(goodId).orElseThrow().getStatus());

        ageRows();
        dispatcher.retryDue();
        assertTrue(repository.findAll().stream().allMatch(row -> row.getAttempts() >= 2 || row.getId().equals(goodId)),
                "every failing row gets its turn before any row goes twice");
    }

    @Test
    void retry_stopsEarly_whenMailsCantBeReached() {
        when(mailsClient.send(any())).thenThrow(new org.springframework.web.client.ResourceAccessException("down"));
        for (int i = 0; i < 20; i++) {
            mailQueue.sendToAddress("TEST", "x" + i + "@example.com", null, "Subject", "Text");
        }
        ageRows();
        clearInvocations(mailsClient);

        assertEquals(OutgoingEmailDispatcher.MAX_UNREACHABLE_IN_A_ROW, dispatcher.retryDue());
        verify(mailsClient, times(OutgoingEmailDispatcher.MAX_UNREACHABLE_IN_A_ROW)).send(any());
    }

    @Test
    void internalEndpoints_needTheToken() throws Exception {
        mockMvc.perform(get("/internal/v1/users/1/contact")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/users/1/contact").header(InternalTokenFilter.HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/outgoing-emails/retry")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSignedInUsersToken_doesNotOpenInternalCalls() throws Exception {
        mockMvc.perform(get("/internal/v1/users/1/contact").header("Authorization", "Bearer anything"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anEmptyConfiguredToken_refusesEverything() throws Exception {
        InternalTokenFilter filter = new InternalTokenFilter("");
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/internal/v1/users/1/contact");
        request.setServletPath("/internal/v1/users/1/contact");
        request.setServletPath(request.getRequestURI()); // What Tomcat sets; a bare mock request has none.
        request.addHeader(InternalTokenFilter.HEADER, "");
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain chain = new org.springframework.mock.web.MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void consentStatus_listsWhatIsPending_andNeedsTheToken() throws Exception {
        User user = new User();
        user.setFirebaseUid("uid-consent-status");
        user.setEmail("consent-status@example.com");
        user.setRoles(Set.of(Role.MOTHER));
        Long id = userRepository.save(user).getId();

        mockMvc.perform(get("/internal/v1/users/" + id + "/consent-status")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/users/" + id + "/consent-status").header(InternalTokenFilter.HEADER, "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").isArray())
                .andExpect(jsonPath("$.pending").isNotEmpty());
    }

    @Test
    void internalEndpoints_answerSignInAndAddressLookups() throws Exception {
        User user = new User();
        user.setFirebaseUid("uid-internal");
        user.setEmail("internal@example.com");
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        Long id = userRepository.save(user).getId();

        mockMvc.perform(get("/internal/v1/users/by-firebase-uid/uid-internal").header(InternalTokenFilter.HEADER, "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id))
                .andExpect(jsonPath("$.roles[0]").value("MOTHER"));
        mockMvc.perform(get("/internal/v1/users/by-firebase-uid/nobody").header(InternalTokenFilter.HEADER, "test-internal-token"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/internal/v1/users/" + id + "/contact").header(InternalTokenFilter.HEADER, "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("internal@example.com"));
    }
}
