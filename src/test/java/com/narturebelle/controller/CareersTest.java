package com.narturebelle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.email.EmailMessage;
import com.narturebelle.email.EmailSender;
import com.narturebelle.model.FeedbackMessage;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.FeedbackMessageRepository;
import com.narturebelle.repository.JobApplicationRepository;
import com.narturebelle.repository.JobApplicationResumeRepository;
import com.narturebelle.repository.JobPostingRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Covers the careers page (admin-posted roles, applications with a résumé emailed to admin) and the feedback form. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareersTest {

    private static final String TOKEN_ADMIN = "careers-token-admin";
    private static final String TOKEN_USER = "careers-token-user";
    private static final byte[] PDF = "%PDF-1.4 test resume".getBytes(StandardCharsets.US_ASCII);
    /** Each test applies from its own IP, so the in-memory rate limiter never leaks between tests. */
    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private JobApplicationResumeRepository resumeRepository;
    @Autowired private FeedbackMessageRepository feedbackMessageRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private EmailSender emailSender;

    private Long userId;
    private String ip;

    @BeforeEach
    void setUp() throws Exception {
        resumeRepository.deleteAll();
        jobApplicationRepository.deleteAll();
        jobPostingRepository.deleteAll();
        feedbackMessageRepository.deleteAll();
        userRepository.deleteAll();

        createUser("uid-careers-admin", "careers-admin@example.com", Role.OPERATIONS_ADMIN);
        userId = createUser("uid-careers-user", "careers-user@example.com", Role.MOTHER);
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-careers-admin", "careers-admin@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_USER))
                .thenReturn(new VerifiedFirebaseToken("uid-careers-user", "careers-user@example.com", true, null, null));
        when(emailSender.delivers()).thenReturn(true);
        ip = "203.0.113." + IP.getAndIncrement();
    }

    private Long createUser(String uid, String email, Role role) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(role));
        return userRepository.save(user).getId();
    }

    private long postJob(String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/careers/jobs").header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"team\":\"Care\",\"location\":\"Bengaluru\","
                                + "\"employmentType\":\"FULL_TIME\",\"workMode\":\"HYBRID\",\"summary\":\"Lead our nurses\","
                                + "\"description\":\"Line one\\nLine two\",\"requirements\":[\"GNM or B.Sc Nursing\",\"Kannada\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.applicationCount").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("id").asLong();
    }

    private MockMultipartHttpServletRequestBuilder application(long jobId, MockMultipartFile resume, String email) {
        return application(jobId, resume, email, "true");
    }

    private MockMultipartHttpServletRequestBuilder application(long jobId, MockMultipartFile resume, String email, String consent) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/careers/jobs/" + jobId + "/applications");
        if (resume != null) builder.file(resume);
        builder.param("fullName", "Asha Rao").param("email", email).param("phone", "+91 98450 12345")
                .param("location", "Bengaluru").param("linkedinUrl", "").param("yearsExperience", "6")
                .param("coverNote", "I'd love to help.").param("privacyConsent", consent);
        builder.with(request -> { request.setRemoteAddr(ip); return request; });
        return builder;
    }

    private static MockMultipartFile resume(String name, byte[] content) {
        return new MockMultipartFile("resume", name, "application/octet-stream", content);
    }

    @Test
    void apply_savesTheApplication_andEmailsAdminWithTheResume() throws Exception {
        long jobId = postJob("Senior Nurse");

        mockMvc.perform(get("/api/v1/careers/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Senior Nurse"))
                .andExpect(jsonPath("$[0].description").value("Line one\nLine two"))
                .andExpect(jsonPath("$[0].requirements[1]").value("Kannada"))
                .andExpect(jsonPath("$[0].applicationCount").doesNotExist());

        mockMvc.perform(application(jobId, resume("../Asha CV.pdf", PDF), "Asha@Example.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.submittedAt").exists());

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(2)).send(sent.capture());
        EmailMessage toAdmin = sent.getAllValues().get(0);
        assertEquals("hello@narturebelle.com", toAdmin.to());
        assertEquals("asha@example.com", toAdmin.replyTo());
        assertTrue(toAdmin.text().contains("+91 98450 12345"));
        assertEquals("Asha CV.pdf", toAdmin.attachments().get(0).fileName());
        assertArrayEquals(PDF, toAdmin.attachments().get(0).content());
        assertEquals("asha@example.com", sent.getAllValues().get(1).to()); // the applicant's confirmation

        mockMvc.perform(get("/api/v1/admin/careers/jobs").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(jsonPath("$[0].applicationCount").value(1));
        String apps = mockMvc.perform(get("/api/v1/admin/careers/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Asha Rao"))
                .andExpect(jsonPath("$[0].linkedinUrl").doesNotExist())
                .andExpect(jsonPath("$[0].resumeFileName").value("Asha CV.pdf"))
                .andExpect(jsonPath("$[0].adminNotified").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long applicationId = objectMapper.readTree(apps).get(0).get("id").asLong();

        byte[] downloaded = mockMvc.perform(get("/api/v1/admin/careers/applications/" + applicationId + "/resume")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(PDF, downloaded);

        // Not for mothers.
        mockMvc.perform(get("/api/v1/admin/careers/jobs").header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    void closedJob_isHiddenAndRefusesApplications_untilReopened() throws Exception {
        long jobId = postJob("Lactation Consultant");
        mockMvc.perform(post("/api/v1/admin/careers/jobs/" + jobId + "/close").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(get("/api/v1/careers/jobs")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/careers/jobs/" + jobId))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
        mockMvc.perform(application(jobId, resume("cv.pdf", PDF), "a@example.com"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOB_CLOSED"));
        mockMvc.perform(application(999_999, resume("cv.pdf", PDF), "a@example.com"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/admin/careers/jobs/" + jobId + "/reopen").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.closedAt").doesNotExist());
        mockMvc.perform(get("/api/v1/careers/jobs/" + jobId)).andExpect(status().isOk());
    }

    @Test
    void resume_mustBeARealPdfOrWordFile() throws Exception {
        long jobId = postJob("Doula");
        mockMvc.perform(application(jobId, resume("cv.exe", PDF), "a@example.com"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESUME_TYPE_NOT_ALLOWED"));
        // Renamed to .pdf but not a PDF inside.
        mockMvc.perform(application(jobId, resume("cv.pdf", "MZ not a pdf".getBytes(StandardCharsets.US_ASCII)), "a@example.com"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESUME_TYPE_NOT_ALLOWED"));
        mockMvc.perform(application(jobId, resume("cv.docx", new byte[]{'P', 'K', 3, 4, 0}), "a@example.com"))
                .andExpect(status().isCreated());
        mockMvc.perform(application(jobId, null, "b@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.resume").exists());
        mockMvc.perform(application(jobId, resume("cv.pdf", PDF), "not-an-email", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.privacyConsent").exists());
        assertEquals(1, jobApplicationRepository.count());
    }

    @Test
    void honeypot_looksSuccessful_butSavesAndSendsNothing() throws Exception {
        long jobId = postJob("Nutritionist");
        mockMvc.perform(application(jobId, resume("cv.pdf", PDF), "bot@example.com").param("website", "http://spam"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"buy now\",\"website\":\"http://spam\"}"))
                .andExpect(status().isCreated());
        assertEquals(0, jobApplicationRepository.count());
        assertEquals(0, feedbackMessageRepository.count());
        verify(emailSender, never()).send(any());
    }

    @Test
    void applications_areRateLimitedPerEmail() throws Exception {
        long jobId = postJob("Physiotherapist");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(application(jobId, resume("cv.pdf", PDF), "same@example.com")).andExpect(status().isCreated());
        }
        mockMvc.perform(application(jobId, resume("cv.pdf", PDF), "same@example.com"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void emailFailure_stillSavesTheApplication() throws Exception {
        doThrow(new RuntimeException("provider down")).when(emailSender).send(any());
        long jobId = postJob("Coordinator");
        mockMvc.perform(application(jobId, resume("cv.pdf", PDF), "c@example.com")).andExpect(status().isCreated());
        assertNull(jobApplicationRepository.findAll().get(0).getAdminNotifiedAt());
    }

    @Test
    void feedback_isSavedAndEmailed_withTheUserIdWhenSignedIn() throws Exception {
        mockMvc.perform(post("/api/v1/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Meera\",\"email\":\"meera@example.com\",\"message\":\"Add Tamil videos\",\"rating\":5,\"page\":\"/joy-circle\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
        mockMvc.perform(post("/api/v1/feedback").header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Love it\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/feedback").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\",\"rating\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.message").exists())
                .andExpect(jsonPath("$.fields.rating").exists());

        List<FeedbackMessage> saved = feedbackMessageRepository.findAll();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(f -> userId.equals(f.getUserId())));
        assertTrue(saved.stream().anyMatch(f -> f.getUserId() == null && f.getAdminNotifiedAt() != null));

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(2)).send(sent.capture());
        assertEquals("hello@narturebelle.com", sent.getAllValues().get(0).to());
        assertEquals("meera@example.com", sent.getAllValues().get(0).replyTo());
        assertTrue(sent.getAllValues().get(0).text().contains("Add Tamil videos"));
    }
}
