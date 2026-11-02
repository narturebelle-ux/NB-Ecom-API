package com.narturebelle.providers.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.providers.dto.FormDtos.FormQuestion;
import com.narturebelle.providers.dto.FormDtos.QuestionType;
import com.narturebelle.mail.MailsClient;
import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.model.BookingStatus;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.model.ProviderStatus;
import com.narturebelle.model.Role;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.model.Booking;
import com.narturebelle.model.Journey;
import com.narturebelle.model.PayoutRate;
import com.narturebelle.model.Provider;
import com.narturebelle.model.SessionFeedback;
import com.narturebelle.model.ProviderSlot;
import com.narturebelle.model.User;
import com.narturebelle.providers.repository.*;
import com.narturebelle.repository.*;
import com.narturebelle.providers.scheduling.ApplicationLifecycleJob;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import com.narturebelle.providers.service.ApplicationDocumentService;
import com.narturebelle.providers.service.ApplicationFormCatalog;
import com.narturebelle.providers.service.ApplicationNotifier;
import com.narturebelle.providers.service.ProviderLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Covers the customer-approved onboarding rules from sign-up to go-live, through the real HTTP endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProviderOnboardingTest {

    private static final byte[] PDF = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);
    private static final String PAN = "ABCDE1234F";
    private static final String OPERATIONS = "token-operations";
    private static final String MEDICAL = "token-medical";
    private static final String FINANCE = "token-finance";
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationFormCatalog catalog;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private PayoutRateRepository payoutRateRepository;
    @Autowired private ProviderSlotRepository slotRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ProviderApplicationRepository applicationRepository;
    @Autowired private ApplicationAnswerRepository answerRepository;
    @Autowired private RequestedChangeRepository requestedChangeRepository;
    @Autowired private LicenceReminderRepository reminderRepository;
    @Autowired private ApplicationDocumentService documentService;
    @Autowired private ProviderLifecycleService lifecycleService;
    @Autowired private ApplicationNotifier notifier;
    @Autowired private AuditService auditService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private AuditEventRepository auditRepository;
    @Autowired private SessionFeedbackRepository feedbackRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private MailsClient mailsClient;

    private String applicant;
    private Long applicantId;

    @BeforeEach
    void setUp() throws Exception {
        createUser(OPERATIONS, Role.OPERATIONS_ADMIN, true, true);
        createUser(MEDICAL, Role.MEDICAL_REVIEWER, true, true);
        createUser(FINANCE, Role.FINANCE_ADMIN, true, true);
        applicant = "token-applicant-" + NEXT.getAndIncrement();
        applicantId = createUser(applicant, Role.MOTHER, true, true);
        if (payoutRateRepository.findByProfessionalType(Role.GYNECOLOGIST).isEmpty()) {
            PayoutRate rate = new PayoutRate();
            rate.setProfessionalType(Role.GYNECOLOGIST);
            rate.setAmount(new BigDecimal("500.00"));
            rate.setUpdatedAt(LocalDateTime.now());
            payoutRateRepository.save(rate);
        }
    }

    private Long createUser(String token, Role role, boolean emailVerified, boolean phoneVerified) throws Exception {
        User user = userRepository.findByFirebaseUid("uid-" + token).orElseGet(User::new);
        user.setFirebaseUid("uid-" + token);
        user.setEmail(token + "@example.com");
        user.setRoles(new java.util.HashSet<>(Set.of(role)));
        Long id = userRepository.save(user).getId();
        when(firebaseTokenVerifier.verify(token)).thenReturn(new VerifiedFirebaseToken("uid-" + token, token + "@example.com",
                emailVerified, phoneVerified ? "+919900000000" : null, null));
        return id;
    }

    // ---- the adaptive form ----

    @Test
    void professionsAreListedPublicly() throws Exception {
        mockMvc.perform(get("/api/v1/public/apply-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professions.length()").value(7))
                .andExpect(jsonPath("$.draft").value(true));
    }

    @Test
    void questionsDependOnProfession() throws Exception {
        String gyn = call(get("/api/v1/provider-application/form").param("profession", "GYNECOLOGIST"), applicant).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String psych = call(get("/api/v1/provider-application/form").param("profession", "CLINICAL_PSYCHOLOGIST"), applicant).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(gyn.contains("pcpndtNumber"));
        assertFalse(psych.contains("pcpndtNumber"));
        assertTrue(psych.contains("RCI CRR number"));
    }

    @Test
    void aNonProfessionCannotBeChosen() throws Exception {
        call(get("/api/v1/provider-application/form").param("profession", "SUPER_ADMIN"), applicant).andExpect(status().isBadRequest());
    }

    // ---- who can apply ----

    @Test
    void aMotherAccountCannotApply() throws Exception {
        Journey journey = new Journey();
        journey.setStatus(com.narturebelle.model.JourneyStatus.PREGNANCY);
        journey.setUserId(applicantId);
        journeyRepository.save(journey);

        start(Role.GYNECOLOGIST).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOTHER_ACCOUNT"));
    }

    @Test
    void termsMustBeAccepted() throws Exception {
        call(post("/api/v1/provider-application").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("profession", "GYNECOLOGIST", "termsAccepted", false, "verificationConsent", true))), applicant)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.termsAccepted").exists());
    }

    @Test
    void submitNeedsVerifiedPhone() throws Exception {
        String unverified = "token-no-phone-" + NEXT.getAndIncrement();
        createUser(unverified, Role.MOTHER, true, false);
        call(post("/api/v1/provider-application").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("profession", "GYNECOLOGIST", "termsAccepted", true, "verificationConsent", true))), unverified)
                .andExpect(status().isCreated());
        call(post("/api/v1/provider-application/submit"), unverified)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_VERIFIED"));
    }

    @Test
    void submitListsEverythingMissing() throws Exception {
        start(Role.GYNECOLOGIST).andExpect(status().isCreated());
        call(post("/api/v1/provider-application/submit"), applicant)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"))
                .andExpect(jsonPath("$.missing.length()").value(org.hamcrest.Matchers.greaterThan(5)));
    }

    // ---- answers ----

    @Test
    void panIsEncryptedAndNeverReturned() throws Exception {
        start(Role.GYNECOLOGIST).andExpect(status().isCreated());
        save(Map.of("pan", PAN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.pan").doesNotExist())
                .andExpect(jsonPath("$.sensitiveSaved[0]").value("pan"));
        String stored = answerRepository.findAll().stream().filter(a -> a.getQuestionKey().equals("pan") && a.getApplicationId().equals(currentApp().getId()))
                .findFirst().orElseThrow().getAnswerValue();
        assertFalse(stored.contains(PAN));
    }

    @Test
    void badAnswersAreRefused() throws Exception {
        start(Role.GYNECOLOGIST).andExpect(status().isCreated());
        save(Map.of("pan", "nope", "pin", "12")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.pan").exists()).andExpect(jsonPath("$.fields.pin").exists());
        save(Map.of("notAQuestion", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void changingProfessionClearsItsAnswers() throws Exception {
        start(Role.GYNECOLOGIST).andExpect(status().isCreated());
        save(Map.of("legalName", "Asha Rao", "pcpndtNumber", "P1")).andExpect(status().isOk());
        call(put("/api/v1/provider-application/profession").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("profession", "NUTRITIONIST"))), applicant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.legalName").value("Asha Rao"))
                .andExpect(jsonPath("$.answers.pcpndtNumber").doesNotExist());
    }

    @Test
    void aFakeDocumentIsRefused() throws Exception {
        start(Role.GYNECOLOGIST).andExpect(status().isCreated());
        MockMultipartFile fake = new MockMultipartFile("file", "licence.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.US_ASCII));
        call(multipart("/api/v1/provider-application/documents/panCard").file(fake), applicant)
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---- review ----

    @Test
    void onlyOperationsAndMedicalReviewersCanReview() throws Exception {
        call(get("/api/v1/admin/provider-applications"), FINANCE).andExpect(status().isForbidden());
        call(get("/api/v1/admin/provider-applications"), applicant).andExpect(status().isForbidden());
        call(get("/api/v1/admin/provider-applications"), OPERATIONS).andExpect(status().isOk());
        call(get("/api/v1/admin/provider-applications"), MEDICAL).andExpect(status().isOk());
    }

    @Test
    void eachStageBelongsToItsOwnRole() throws Exception {
        Long id = submitted(Role.GYNECOLOGIST, "REG-" + NEXT.getAndIncrement());
        decide(id, "medical", MEDICAL, approval()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WRONG_REVIEW_STAGE"));
        decide(id, "operations", MEDICAL, Map.of("decision", "PASS")).andExpect(status().isForbidden());
        decide(id, "operations", OPERATIONS, Map.of("decision", "PASS")).andExpect(status().isOk());
        decide(id, "operations", OPERATIONS, Map.of("decision", "PASS")).andExpect(status().isConflict());
        decide(id, "medical", OPERATIONS, approval()).andExpect(status().isForbidden());
    }

    @Test
    void theSamePersonCannotDoBothStages() throws Exception {
        String both = "token-both-" + NEXT.getAndIncrement();
        Long bothId = createUser(both, Role.OPERATIONS_ADMIN, true, true);
        User user = userRepository.findById(bothId).orElseThrow();
        user.getRoles().add(Role.MEDICAL_REVIEWER);
        userRepository.save(user);

        Long id = submitted(Role.GYNECOLOGIST, "REG-" + NEXT.getAndIncrement());
        decide(id, "operations", both, Map.of("decision", "PASS")).andExpect(status().isOk());
        decide(id, "medical", both, approval()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAME_REVIEWER"));
    }

    @Test
    void noLicenceCanSubmitButNotPass() throws Exception {
        Long id = submitted(Role.NUTRITIONIST, null);
        call(get("/api/v1/admin/provider-applications/" + id), OPERATIONS).andExpect(status().isOk())
                .andExpect(jsonPath("$.flags[0]").value("NO_REGISTRATION"));
        decide(id, "operations", OPERATIONS, Map.of("decision", "PASS"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_NOT_APPROVABLE"));
    }

    @Test
    void aDuplicateRegistrationCannotPass() throws Exception {
        String number = "DUP-" + NEXT.getAndIncrement();
        submitted(Role.GYNECOLOGIST, number);

        applicant = "token-applicant-" + NEXT.getAndIncrement();
        applicantId = createUser(applicant, Role.MOTHER, true, true);
        Long second = submitted(Role.GYNECOLOGIST, number);
        decide(second, "operations", OPERATIONS, Map.of("decision", "PASS"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.flags[0]").value("DUPLICATE_REGISTRATION"));
    }

    @Test
    void sentBackOnlyReopensTheFlaggedFields() throws Exception {
        Long id = submitted(Role.GYNECOLOGIST, "REG-" + NEXT.getAndIncrement());
        Map<String, Object> changes = Map.of("decision", "CHANGES", "reason", "Name mismatch",
                "changes", List.of(Map.of("questionKey", "legalName", "note", "Doesn't match the certificate")));
        decide(id, "operations", OPERATIONS, changes).andExpect(status().isOk());

        call(get("/api/v1/provider-application"), applicant).andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.requestedChanges[0].questionKey").value("legalName"));
        save(Map.of("city", "Pune")).andExpect(status().isBadRequest());
        save(Map.of("legalName", "Asha R Rao")).andExpect(status().isOk());
        call(post("/api/v1/provider-application/submit"), applicant).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPERATIONS_REVIEW"))
                .andExpect(jsonPath("$.requestedChanges.length()").value(0));
    }

    @Test
    void rejectionSetsTheReapplyDate() throws Exception {
        Long id = submitted(Role.GYNECOLOGIST, "REG-" + NEXT.getAndIncrement());
        decide(id, "operations", OPERATIONS, Map.of("decision", "REJECT")).andExpect(status().isBadRequest());
        decide(id, "operations", OPERATIONS, Map.of("decision", "REJECT", "reason", "Not on the register")).andExpect(status().isOk());
        start(Role.GYNECOLOGIST).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REAPPLY_NOT_YET"));
    }

    @Test
    void fraudBlocksPermanently() throws Exception {
        Long id = submitted(Role.GYNECOLOGIST, "REG-" + NEXT.getAndIncrement());
        decide(id, "operations", OPERATIONS, Map.of("decision", "REJECT", "reason", "Edited certificate", "fraud", true)).andExpect(status().isOk());
        start(Role.GYNECOLOGIST).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("APPLICATION_BLOCKED"));
    }

    @Test
    void anApplicantCanWithdrawAndStartAgain() throws Exception {
        start(Role.GYNECOLOGIST).andExpect(status().isCreated());
        call(post("/api/v1/provider-application/withdraw"), applicant).andExpect(jsonPath("$.status").value("WITHDRAWN"));
        start(Role.PEDIATRICIAN).andExpect(status().isCreated());
    }

    // ---- approval to go-live ----

    @Test
    void aProfessionalSeesFeedbackWithoutTheMothersIdentity() throws Exception {
        Long id = live();
        Long providerId = applicationRepository.findById(id).orElseThrow().getProviderId();

        call(get("/api/v1/provider-workspace/feedback"), applicant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.averageRating").doesNotExist());

        addSlot(LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(5).plusMinutes(30)).andExpect(status().is2xxSuccessful());
        Booking booking = booking(providerId);
        SessionFeedback feedback = new SessionFeedback();
        feedback.setBookingId(booking.getId());
        feedback.setUserId(booking.getUserId());
        feedback.setRating(4);
        feedback.setComment("Very kind");
        feedbackRepository.save(feedback);

        String body = call(get("/api/v1/provider-workspace/feedback"), applicant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.items[0].rating").value(4))
                .andExpect(jsonPath("$.items[0].comment").value("Very kind"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("bookingId"));
        assertFalse(body.contains("userId"));
    }

    @Test
    void theFullJourneyToGoLive() throws Exception {
        Long id = approved();

        // Approved, but hidden: NB-Ecom-API doesn't list or book UNDER_REVIEW, and no professional role yet.
        ProviderApplication application = applicationRepository.findById(id).orElseThrow();
        assertEquals(ApplicationStatus.VERIFIED, application.getStatus());
        assertTrue(application.isCanPrescribe());
        Provider provider = providerRepository.findById(application.getProviderId()).orElseThrow();
        assertEquals(ProviderStatus.UNDER_REVIEW, provider.getStatus());
        assertFalse(userRepository.findById(applicantId).orElseThrow().getRoles().contains(Role.GYNECOLOGIST));
        mockMvc.perform(get("/api/v1/public/providers/" + provider.getId() + "/credentials")).andExpect(status().isNotFound());

        // Go-live refuses until setup is finished, and says what's left.
        call(post("/api/v1/admin/provider-applications/" + id + "/go-live"), OPERATIONS)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SETUP_INCOMPLETE"))
                .andExpect(jsonPath("$.missing.length()").value(6));

        finishSetup(id);

        call(post("/api/v1/admin/provider-applications/" + id + "/go-live"), MEDICAL).andExpect(status().isForbidden());
        call(post("/api/v1/admin/provider-applications/" + id + "/go-live"), OPERATIONS).andExpect(status().isOk());

        assertEquals(ProviderStatus.ACTIVE, providerRepository.findById(provider.getId()).orElseThrow().getStatus());
        assertTrue(userRepository.findById(applicantId).orElseThrow().getRoles().contains(Role.GYNECOLOGIST));
        assertFalse(userRepository.findById(applicantId).orElseThrow().getRoles().contains(Role.MOTHER));
        mockMvc.perform(get("/api/v1/public/providers/" + provider.getId() + "/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").exists())
                .andExpect(jsonPath("$.confirmedExperienceYears").value(8));
    }

    @Test
    void onlyDoctorsCanPrescribe() throws Exception {
        assertTrue(catalog.canPrescribe(Role.PEDIATRICIAN));
        assertFalse(catalog.canPrescribe(Role.NUTRITIONIST));
        assertFalse(catalog.canPrescribe(Role.REGISTERED_NURSE));
        assertFalse(catalog.canPrescribe(Role.CLINICAL_PSYCHOLOGIST));
    }

    @Test
    void agreementNeedsTheLegalName() throws Exception {
        approved();
        call(post("/api/v1/provider-application/setup/agreement").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("typedName", "Someone Else"))), applicant)
                .andExpect(status().isBadRequest());
    }

    @Test
    void trainingMustBeAllCorrect() throws Exception {
        approved();
        call(post("/api/v1/provider-application/setup/training").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("answers", Map.of("privacy", 0, "emergency", 1, "sex", 2, "products", 0)))), applicant)
                .andExpect(status().isOk()).andExpect(jsonPath("$.passed").value(false)).andExpect(jsonPath("$.correct").value(3));
    }

    @Test
    void bankNumberIsMaskedForTheProfessional() throws Exception {
        approved();
        saveBank().andExpect(status().isOk()).andExpect(jsonPath("$.accountLast4").value("7890"))
                .andExpect(jsonPath("$.accountNumber").doesNotExist())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
    }

    @Test
    void slotsCannotOverlap() throws Exception {
        approved();
        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        addSlot(start, start.plusMinutes(30)).andExpect(status().isCreated());
        addSlot(start.plusMinutes(15), start.plusMinutes(45)).andExpect(status().isConflict());
        addSlot(start.minusDays(10), start.minusDays(10).plusMinutes(30)).andExpect(status().isBadRequest());
    }

    // ---- once live ----

    @Test
    void suspendingCancelsUpcomingBookingsWithoutCharge() throws Exception {
        Long id = live();
        ProviderApplication application = applicationRepository.findById(id).orElseThrow();
        Booking booking = booking(application.getProviderId());

        call(post("/api/v1/admin/provider-applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "SUSPENDED", "reason", "Complaint under review"))), OPERATIONS).andExpect(status().isOk());

        assertEquals(ProviderStatus.SUSPENDED, providerRepository.findById(application.getProviderId()).orElseThrow().getStatus());
        Booking cancelled = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertFalse(cancelled.getCancelReason().contains("Complaint"));
    }

    @Test
    void aProfileChangeWaitsForApproval() throws Exception {
        Long id = live();
        Long providerId = applicationRepository.findById(id).orElseThrow().getProviderId();
        String body = call(post("/api/v1/provider-workspace/change-requests").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "PROFILE", "values", Map.of("bio", "New bio")))), applicant)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long changeId = objectMapper.readTree(body).get("id").asLong();
        assertNotEquals("New bio", providerRepository.findById(providerId).orElseThrow().getBio());

        call(post("/api/v1/admin/provider-applications/change-requests/" + changeId + "/decision").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("approve", true))), OPERATIONS).andExpect(status().isOk());
        assertEquals("New bio", providerRepository.findById(providerId).orElseThrow().getBio());
    }

    @Test
    void aRenewedLicenceRestoresALapsedProfessional() throws Exception {
        Long id = live();
        ProviderApplication application = applicationRepository.findById(id).orElseThrow();
        application.setLicenceExpiresOn(LocalDate.now().minusDays(1));
        applicationRepository.save(application);
        job().runDaily();
        assertEquals(ApplicationStatus.PAUSED, applicationRepository.findById(id).orElseThrow().getStatus());
        assertEquals(ProviderStatus.PAUSED, providerRepository.findById(application.getProviderId()).orElseThrow().getStatus());

        call(post("/api/v1/admin/provider-applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "ACTIVE", "reason", "Back"))), OPERATIONS)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LICENCE_LAPSED"));

        String expiry = LocalDate.now().plusYears(5).toString();
        String body = call(post("/api/v1/provider-workspace/change-requests").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "LICENCE", "values", Map.of("registrationExpiry", expiry)))), applicant)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long changeId = objectMapper.readTree(body).get("id").asLong();

        call(post("/api/v1/admin/provider-applications/change-requests/" + changeId + "/decision").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("approve", true))), OPERATIONS)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHANGE_DOCUMENT_MISSING"));

        call(multipart("/api/v1/provider-workspace/change-requests/" + changeId + "/document")
                .file(new MockMultipartFile("file", "renewed.pdf", "application/pdf", PDF)), applicant).andExpect(status().isOk());
        call(post("/api/v1/admin/provider-applications/change-requests/" + changeId + "/decision").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("approve", true))), OPERATIONS).andExpect(status().isOk());

        ProviderApplication restored = applicationRepository.findById(id).orElseThrow();
        assertEquals(ApplicationStatus.ACTIVE, restored.getStatus());
        assertFalse(restored.isLicenceLapsed());
        assertEquals(LocalDate.parse(expiry), restored.getLicenceExpiresOn());
        assertEquals(ProviderStatus.ACTIVE, providerRepository.findById(application.getProviderId()).orElseThrow().getStatus());
    }

    // ---- loopholes found in review ----

    private long requestChange(String type, Map<String, Object> values) throws Exception {
        String body = call(post("/api/v1/provider-workspace/change-requests").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", type, "values", values))), applicant)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private static Map<String, Object> newBank() {
        return Map.of("accountHolderName", "Asha Rao", "accountNumber", "9999888877", "accountNumberConfirm", "9999888877",
                "ifsc", "ICIC0001234", "accountType", "CURRENT", "gstRegistered", false);
    }

    private ResultActions decideChange(long changeId, String token, boolean approve) throws Exception {
        return call(post("/api/v1/admin/provider-applications/change-requests/" + changeId + "/decision").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("approve", approve))), token);
    }

    @Test
    void aBankChangeLeavesTheOldAccountUntilApproved() throws Exception {
        live();
        long changeId = requestChange("BANK", newBank());

        call(get("/api/v1/provider-workspace/profile"), applicant)
                .andExpect(jsonPath("$.bank.accountLast4").value("7890")).andExpect(jsonPath("$.bank.verificationStatus").value("PASSED"));

        decideChange(changeId, OPERATIONS, false).andExpect(status().isOk());
        call(get("/api/v1/provider-workspace/profile"), applicant)
                .andExpect(jsonPath("$.bank.accountLast4").value("7890")).andExpect(jsonPath("$.bank.verificationStatus").value("PASSED"));

        long second = requestChange("BANK", newBank());
        decideChange(second, OPERATIONS, true).andExpect(status().isOk());
        call(get("/api/v1/provider-workspace/profile"), applicant)
                .andExpect(jsonPath("$.bank.accountLast4").value("8877")).andExpect(jsonPath("$.bank.verificationStatus").value("PENDING"));
    }

    @Test
    void onlyOperationsDecidesBankAndStatus() throws Exception {
        Long id = live();
        long changeId = requestChange("BANK", newBank());
        decideChange(changeId, MEDICAL, true).andExpect(status().isForbidden());
        call(post("/api/v1/admin/provider-applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "SUSPENDED", "reason", "No"))), MEDICAL).andExpect(status().isForbidden());
    }

    @Test
    void medicalReviewersNeverSeeFullAccountNumbers() throws Exception {
        live();
        requestChange("BANK", newBank());
        String asMedical = call(get("/api/v1/admin/provider-applications/change-requests"), MEDICAL).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(asMedical.contains("9999888877"));
        assertTrue(asMedical.contains("ending 8877"));
        String asOperations = call(get("/api/v1/admin/provider-applications/change-requests"), OPERATIONS).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(asOperations.contains("9999888877"));
        assertFalse(asOperations.contains("accountNumberConfirm"));
    }

    @Test
    void staffCannotActOnTheirOwnApplication() throws Exception {
        Long id = live();
        long changeId = requestChange("PROFILE", Map.of("bio", "Self approved"));
        User user = userRepository.findById(applicantId).orElseThrow();
        user.getRoles().add(Role.OPERATIONS_ADMIN);
        userRepository.save(user);

        decideChange(changeId, applicant, true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAME_REVIEWER"));
        call(post("/api/v1/admin/provider-applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "PAUSED", "reason", "Mine"))), applicant).andExpect(status().isConflict());
    }

    @Test
    void aNewSlotCannotOverlapABookedOne() throws Exception {
        Long id = live();
        Long providerId = applicationRepository.findById(id).orElseThrow().getProviderId();
        ProviderSlot slot = slotRepository.findByProviderIdAndStatusAndStartAtAfterOrderByStartAtAsc(providerId, SlotStatus.OPEN, LocalDateTime.now()).get(0);
        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);

        addSlot(slot.getStartAt().plusMinutes(10), slot.getEndAt().plusMinutes(10)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_OVERLAP"));
    }

    @Test
    void aLockedDocumentCannotBeDeleted() throws Exception {
        Long id = submitted(Role.GYNECOLOGIST, "REG-" + NEXT.getAndIncrement());
        decide(id, "operations", OPERATIONS, Map.of("decision", "CHANGES", "changes", List.of(Map.of("questionKey", "legalName")))).andExpect(status().isOk());

        call(delete("/api/v1/provider-application/documents/panCard"), applicant).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FIELD_LOCKED"));
        assertTrue(documentService.list(id).stream().anyMatch(d -> d.getQuestionKey().equals("panCard")));
    }

    @Test
    void aHiddenRegistrationNumberIsNotKept() throws Exception {
        start(Role.POSTPARTUM_PHYSIOTHERAPIST).andExpect(status().isCreated());
        Map<String, FormQuestion> byKey = catalog.questionsByKey(Role.POSTPARTUM_PHYSIOTHERAPIST);
        Map<String, Object> answers = new HashMap<>();
        Map<String, String> plain = new HashMap<>();
        for (FormQuestion question : byKey.values()) {
            if (question.type() == QuestionType.FILE) continue;
            Object value = question.key().equals("stateHasCouncil") ? "NO" : question.key().equals(ApplicationFormCatalog.KEY_ALLIED_HEALTH_ID) ? "AH-777"
                    : answerFor(question, "STALE-111");
            if (catalog.isVisible(question, plain, byKey) || question.key().equals(ApplicationFormCatalog.KEY_REGISTRATION_NUMBER)) {
                answers.put(question.key(), value);
                plain.put(question.key(), value instanceof List<?> list ? String.valueOf(list.get(0)) : String.valueOf(value));
            }
        }
        save(answers).andExpect(status().isOk());
        for (FormQuestion question : byKey.values()) {
            if (question.type() == QuestionType.FILE && catalog.isVisible(question, plain, byKey)) {
                call(multipart("/api/v1/provider-application/documents/" + question.key())
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF)), applicant).andExpect(status().isCreated());
            }
        }
        call(post("/api/v1/provider-application/submit"), applicant).andExpect(status().isOk());
        assertEquals("AH-777", currentApp().getRegistrationNumber());
    }

    @Test
    void changeRequestsRefuseMissingValues() throws Exception {
        live();
        Map<String, Object> blank = new HashMap<>();
        blank.put("legalName", null);
        call(post("/api/v1/provider-workspace/change-requests").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "NAME", "values", blank))), applicant).andExpect(status().isBadRequest());
        Map<String, Object> noBio = new HashMap<>();
        noBio.put("bio", null);
        call(post("/api/v1/provider-workspace/change-requests").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "PROFILE", "values", noBio))), applicant).andExpect(status().isBadRequest());
    }

    @Test
    void aLicenceChangeCannotClearALapseWithoutANewDate() throws Exception {
        Long id = live();
        ProviderApplication application = applicationRepository.findById(id).orElseThrow();
        application.setLicenceExpiresOn(LocalDate.now().minusDays(1));
        applicationRepository.save(application);
        job().runDaily();

        call(post("/api/v1/provider-workspace/change-requests").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "LICENCE", "values", Map.of("registrationNumber", "NEW-1")))), applicant)
                .andExpect(status().isBadRequest());
    }

    @Test
    void anExpiredLicenceCannotGoLive() throws Exception {
        Long id = approved();
        finishSetup(id);
        ProviderApplication application = applicationRepository.findById(id).orElseThrow();
        application.setLicenceExpiresOn(LocalDate.now().minusDays(1));
        applicationRepository.save(application);

        call(post("/api/v1/admin/provider-applications/" + id + "/go-live"), OPERATIONS).andExpect(status().isConflict());

        job().runDaily();
        assertTrue(applicationRepository.findById(id).orElseThrow().isLicenceLapsed());
    }

    @Test
    void anExpiredLicenceCannotBeRestored() throws Exception {
        Long id = live();
        call(post("/api/v1/admin/provider-applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "PAUSED", "reason", "Holiday"))), OPERATIONS).andExpect(status().isOk());
        ProviderApplication application = applicationRepository.findById(id).orElseThrow();
        application.setLicenceExpiresOn(LocalDate.now().minusDays(1));
        applicationRepository.save(application);

        call(post("/api/v1/admin/provider-applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "ACTIVE", "reason", "Back"))), OPERATIONS)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LICENCE_LAPSED"));
    }

    @Test
    void anExpiredLicenceBlocksApproval() throws Exception {
        start(Role.GYNECOLOGIST).andExpect(status().isCreated());
        Long id = currentApp().getId();
        // Submit normally, then let the date on file pass before review.
        applicationRepository.deleteById(id);
        Long submittedId = submitted(Role.GYNECOLOGIST, "REG-" + NEXT.getAndIncrement());
        answerRepository.findByApplicationIdAndQuestionKey(submittedId, ApplicationFormCatalog.KEY_REGISTRATION_EXPIRY).ifPresent(answerRepository::delete);
        var expiry = new com.narturebelle.providers.model.ApplicationAnswer();
        expiry.setApplicationId(submittedId);
        expiry.setQuestionKey(ApplicationFormCatalog.KEY_REGISTRATION_EXPIRY);
        expiry.setAnswerValue(LocalDate.now().minusDays(1).toString());
        answerRepository.save(expiry);

        decide(submittedId, "operations", OPERATIONS, Map.of("decision", "PASS"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.flags[0]").value("LICENCE_EXPIRED"));
    }

    @Test
    void cancelledBookingsAreAudited() throws Exception {
        Long id = live();
        Booking booking = booking(applicationRepository.findById(id).orElseThrow().getProviderId());
        call(post("/api/v1/admin/provider-applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "SUSPENDED", "reason", "Complaint"))), OPERATIONS).andExpect(status().isOk());
        assertTrue(auditRepository.findAll().stream().anyMatch(e -> "BOOKING_CANCELLED".equals(e.getAction()) && String.valueOf(booking.getId()).equals(e.getSubjectId())));
    }

    @Test
    void aFailedDocumentUploadCanBeRetried() throws Exception {
        live();
        long changeId = requestChange("LICENCE", Map.of("registrationExpiry", LocalDate.now().plusYears(5).toString()));
        call(get("/api/v1/provider-workspace/change-requests"), applicant)
                .andExpect(jsonPath("$[0].documentRequired").value(true)).andExpect(jsonPath("$[0].documentAttached").value(false));
        call(multipart("/api/v1/provider-workspace/change-requests/" + changeId + "/document")
                .file(new MockMultipartFile("file", "renewed.pdf", "application/pdf", PDF)), applicant)
                .andExpect(status().isOk()).andExpect(jsonPath("$.documentAttached").value(true));
    }

    // ---- the daily job ----

    @Test
    void licenceRemindersSendOncePerWindow() throws Exception {
        Long id = live();
        ProviderApplication application = applicationRepository.findById(id).orElseThrow();
        application.setLicenceExpiresOn(LocalDate.now().plusDays(25));
        applicationRepository.save(application);

        job().runDaily();
        job().runDaily();

        long sent = reminderRepository.findAll().stream().filter(r -> r.getApplicationId().equals(id)).count();
        assertEquals(1, sent);
        assertEquals(30, reminderRepository.findAll().stream().filter(r -> r.getApplicationId().equals(id)).findFirst().orElseThrow().getDaysBefore());
    }

    @Test
    void anAbandonedDraftIsDeleted() throws Exception {
        start(Role.GYNECOLOGIST).andExpect(status().isCreated());
        ProviderApplication application = currentApp();
        application.setLastActivityAt(LocalDateTime.now().minusDays(91));
        applicationRepository.save(application);

        job().runDaily();

        assertTrue(applicationRepository.findById(application.getId()).isEmpty());
    }

    @Test
    void unansweredChangesCloseTheApplication() throws Exception {
        Long id = submitted(Role.GYNECOLOGIST, "REG-" + NEXT.getAndIncrement());
        decide(id, "operations", OPERATIONS, Map.of("decision", "CHANGES", "changes", List.of(Map.of("questionKey", "legalName")))).andExpect(status().isOk());
        ProviderApplication application = applicationRepository.findById(id).orElseThrow();
        application.setChangesRequestedAt(LocalDateTime.now().minusDays(31));
        applicationRepository.save(application);

        job().runDaily();

        assertEquals(ApplicationStatus.CLOSED, applicationRepository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void approvalIsRecheckedWhenSetupStalls() throws Exception {
        Long id = approved();
        finishSetup(id);
        ProviderApplication application = applicationRepository.findById(id).orElseThrow();
        application.setVerifiedAt(LocalDateTime.now().minusDays(61));
        applicationRepository.save(application);

        job().runDaily();

        call(post("/api/v1/admin/provider-applications/" + id + "/go-live"), OPERATIONS).andExpect(status().isConflict());
        call(post("/api/v1/admin/provider-applications/" + id + "/recheck-complete"), OPERATIONS).andExpect(status().isOk());
        call(post("/api/v1/admin/provider-applications/" + id + "/go-live"), OPERATIONS).andExpect(status().isOk());
    }

    @Test
    void closedApplicationsArePurgedKeepingTheFinding() throws Exception {
        String number = "REG-" + NEXT.getAndIncrement();
        Long id = submitted(Role.GYNECOLOGIST, number);
        decide(id, "operations", OPERATIONS, Map.of("decision", "REJECT", "reason", "Not on the register")).andExpect(status().isOk());
        ProviderApplication application = applicationRepository.findById(id).orElseThrow();
        application.setClosedAt(LocalDateTime.now().minusMonths(13));
        applicationRepository.save(application);

        job().runDaily();

        ProviderApplication purged = applicationRepository.findById(id).orElseThrow();
        assertNotNull(purged.getPurgedAt());
        assertEquals(number, purged.getRegistrationNumber());
        assertEquals("Not on the register", purged.getRejectionReason());
        assertTrue(answerRepository.findByApplicationId(id).isEmpty());
        assertTrue(documentService.list(id).isEmpty());
    }

    // ---- helpers ----

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions start(Role profession) throws Exception {
        return call(post("/api/v1/provider-application").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("profession", profession.name(), "termsAccepted", true, "verificationConsent", true))), applicant);
    }

    private ResultActions save(Map<String, Object> answers) throws Exception {
        return call(patch("/api/v1/provider-application/answers").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("answers", answers))), applicant);
    }

    private ResultActions decide(Long id, String stage, String token, Map<String, Object> body) throws Exception {
        return call(post("/api/v1/admin/provider-applications/" + id + "/" + stage + "-decision").contentType(MediaType.APPLICATION_JSON).content(json(body)), token);
    }

    private static Map<String, Object> approval() {
        return Map.of("decision", "APPROVE", "confirmedExperienceYears", 8, "clearedStages", List.of("PREGNANCY", "POSTPARTUM"));
    }

    private ProviderApplication currentApp() {
        return applicationRepository.findFirstByUserIdOrderByCreatedAtDesc(applicantId).orElseThrow();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** Answers every visible required question, so a null registration number means "no licence held". */
    private Long submitted(Role profession, String registrationNumber) throws Exception {
        start(profession).andExpect(status().isCreated());
        Map<String, FormQuestion> byKey = catalog.questionsByKey(profession);
        Map<String, Object> answers = new HashMap<>();
        Map<String, String> plain = new HashMap<>();
        // Parents come before their children in the form, so one pass sees each parent's answer first.
        for (FormQuestion question : byKey.values()) {
            if (question.type() == QuestionType.FILE || !catalog.isVisible(question, plain, byKey)) {
                continue;
            }
            Object value = answerFor(question, registrationNumber);
            answers.put(question.key(), value);
            plain.put(question.key(), value instanceof List<?> list ? String.join("\n", list.stream().map(String::valueOf).toList()) : String.valueOf(value));
        }
        save(answers).andExpect(status().isOk());
        for (FormQuestion question : byKey.values()) {
            if (question.type() == QuestionType.FILE && catalog.isVisible(question, plain, byKey)) {
                call(multipart("/api/v1/provider-application/documents/" + question.key())
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF)), applicant).andExpect(status().isCreated());
            }
        }
        call(post("/api/v1/provider-application/submit"), applicant).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPERATIONS_REVIEW"));
        return currentApp().getId();
    }

    private static Object answerFor(FormQuestion question, String registrationNumber) {
        return switch (question.key()) {
            case ApplicationFormCatalog.KEY_HOLDS_REGISTRATION -> registrationNumber == null ? "NO" : "YES";
            case "currentlyPractising", "stateHasCouncil", "registeredDietitian" -> "YES";
            case ApplicationFormCatalog.KEY_REGISTRATION_NUMBER -> registrationNumber;
            case "pan" -> PAN;
            case "pin", "clinicPin" -> "560001";
            case "referencePhone" -> "+91 99000 00000";
            case ApplicationFormCatalog.KEY_LEGAL_NAME -> "Asha Rao";
            default -> switch (question.type()) {
                case YES_NO -> "NO";
                case SELECT -> question.options().get(0).value();
                case MULTI_SELECT -> List.of(question.options().get(0).value());
                case DATE -> LocalDate.now().minusYears(30).toString();
                case YEAR -> "2010";
                case NUMBER -> "8";
                case CONFIRM -> "true";
                default -> "Test " + question.key();
            };
        };
    }

    private Long approved() throws Exception {
        Long id = submitted(Role.GYNECOLOGIST, "REG-" + NEXT.getAndIncrement());
        decide(id, "operations", OPERATIONS, Map.of("decision", "PASS")).andExpect(status().isOk());
        decide(id, "medical", MEDICAL, approval()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFIED"));
        return id;
    }

    private Long live() throws Exception {
        Long id = approved();
        finishSetup(id);
        call(post("/api/v1/admin/provider-applications/" + id + "/go-live"), OPERATIONS).andExpect(status().isOk());
        return id;
    }

    private void finishSetup(Long id) throws Exception {
        call(post("/api/v1/provider-application/setup/agreement").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("typedName", "Asha Rao"))), applicant)
                .andExpect(status().isOk());
        saveBank().andExpect(status().isOk());
        call(post("/api/v1/admin/provider-applications/" + id + "/bank-verification").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("passed", true))), OPERATIONS)
                .andExpect(status().isOk());
        call(post("/api/v1/provider-application/setup/training").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("answers", Map.of("privacy", 1, "emergency", 1, "sex", 2, "products", 0)))), applicant)
                .andExpect(jsonPath("$.passed").value(true));
        call(post("/api/v1/provider-workspace/services").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Consultation", "modality", "VIRTUAL", "durationMinutes", 30))), applicant).andExpect(status().isCreated());
        LocalDateTime start = LocalDateTime.now().plusDays(3).withNano(0);
        addSlot(start, start.plusMinutes(30)).andExpect(status().isCreated());
        call(post("/api/v1/provider-application/setup/profile-confirmation"), applicant).andExpect(status().isOk());
    }

    private ResultActions saveBank() throws Exception {
        return call(put("/api/v1/provider-application/setup/bank").contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                "accountHolderName", "Asha Rao", "accountNumber", "1234567890", "accountNumberConfirm", "1234567890",
                "ifsc", "HDFC0001234", "accountType", "SAVINGS", "gstRegistered", false))), applicant);
    }

    private ResultActions addSlot(LocalDateTime start, LocalDateTime end) throws Exception {
        return call(post("/api/v1/provider-workspace/slots").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("slots", List.of(Map.of("startAt", start.toString(), "endAt", end.toString()))))), applicant);
    }

    private Booking booking(Long providerId) {
        ProviderSlot slot = slotRepository.findByProviderIdAndStatusAndStartAtAfterOrderByStartAtAsc(providerId, SlotStatus.OPEN, LocalDateTime.now()).get(0);
        Booking booking = new Booking();
        booking.setUserId(applicantId);
        booking.setProviderId(providerId);
        booking.setServiceOfferingId(1L);
        booking.setSlotId(slot.getId());
        booking.setProfessionalType(Role.GYNECOLOGIST);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setScheduledStart(slot.getStartAt());
        booking.setScheduledEnd(slot.getEndAt());
        return bookingRepository.save(booking);
    }

    private ApplicationLifecycleJob job() {
        return new ApplicationLifecycleJob(applicationRepository, answerRepository, requestedChangeRepository, reminderRepository, documentService,
                lifecycleService, notifier, auditService, transactionTemplate, 3, 14, 90, 30, 60, List.of(60, 30, 7), 12);
    }
}
