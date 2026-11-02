package com.narturebelle.controller;

import com.narturebelle.model.Booking;
import com.narturebelle.model.BookingStatus;
import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.Journey;
import com.narturebelle.model.JourneyStatus;
import com.narturebelle.model.Payment;
import com.narturebelle.model.PaymentStatus;
import com.narturebelle.model.Provider;
import com.narturebelle.model.ProviderStatus;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.AuditEventRepository;
import com.narturebelle.repository.BookingRepository;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.repository.JourneyRepository;
import com.narturebelle.repository.PaymentRepository;
import com.narturebelle.repository.PaymentReconciliationFlagRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.scheduling.PaymentReconciliationJob;
import com.narturebelle.scheduling.ReminderGenerationJob;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import com.narturebelle.service.ConsentService;
import com.narturebelle.service.DashboardService;
import com.narturebelle.mail.DueReminder;
import com.narturebelle.mail.MailsClient;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/** Covers the Care side of reminders (deciding what is due and checking consent) and the
 * payment-reconciliation dedupe job. Preferences and reminder rows are tested in NB-Ecom-Notifications-API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationsTest {

    private static final String TOKEN = "notif-token-a";
    private static final String TOKEN_OTHER = "notif-token-other";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentReconciliationFlagRepository paymentReconciliationFlagRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private ConsentService consentService;
    @Autowired private DashboardService dashboardService;
    @Autowired private AuditService auditService;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private MailsClient mailsClient;

    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUp() throws Exception {
        auditEventRepository.deleteAll();
        paymentReconciliationFlagRepository.deleteAll();
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        providerRepository.deleteAll();
        journeyRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        userId = createConsentedUser("uid-notif-a", "notif-a@example.com");
        otherUserId = createConsentedUser("uid-notif-other", "notif-other@example.com");

        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-notif-a", "notif-a@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_OTHER))
                .thenReturn(new VerifiedFirebaseToken("uid-notif-other", "notif-other@example.com", true, null, null));
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

    @Test
    void reminderGenerationJob_tellsMailsAboutADueBooking() {
        Provider provider = new Provider();
        provider.setUserId(otherUserId);
        provider.setProfessionalType(Role.GYNECOLOGIST);
        provider.setStatus(ProviderStatus.ACTIVE);
        Long providerId = providerRepository.save(provider).getId();

        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setProviderId(providerId);
        booking.setServiceOfferingId(1L);
        booking.setSlotId(1L);
        booking.setProfessionalType(Role.GYNECOLOGIST);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setScheduledStart(LocalDateTime.now().plusHours(2));
        booking.setScheduledEnd(LocalDateTime.now().plusHours(2).plusMinutes(30));
        bookingRepository.save(booking);

        ReminderGenerationJob job = new ReminderGenerationJob(bookingRepository, journeyRepository,
                userRepository, dashboardService, consentService, mailsClient, "test-secret");
        job.generateBookingReminders();

        org.mockito.ArgumentCaptor<java.util.List<DueReminder>> sent = org.mockito.ArgumentCaptor.captor();
        org.mockito.Mockito.verify(mailsClient).sendReminders(sent.capture());
        assertEquals(1, sent.getValue().size());
        assertEquals("BOOKING_UPCOMING", sent.getValue().get(0).type());
        assertEquals(userId, sent.getValue().get(0).userId());
    }

    @Test
    void reminderGenerationJob_skipsUserWithPendingConsent() {
        Long unconsentedUserId = createUser("uid-notif-unconsented", "notif-unconsented@example.com");

        Provider provider = new Provider();
        provider.setUserId(otherUserId);
        provider.setProfessionalType(Role.GYNECOLOGIST);
        provider.setStatus(ProviderStatus.ACTIVE);
        Long providerId = providerRepository.save(provider).getId();

        Booking booking = new Booking();
        booking.setUserId(unconsentedUserId);
        booking.setProviderId(providerId);
        booking.setServiceOfferingId(1L);
        booking.setSlotId(2L);
        booking.setProfessionalType(Role.GYNECOLOGIST);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setScheduledStart(LocalDateTime.now().plusHours(2));
        booking.setScheduledEnd(LocalDateTime.now().plusHours(2).plusMinutes(30));
        bookingRepository.save(booking);

        ReminderGenerationJob job = new ReminderGenerationJob(bookingRepository, journeyRepository,
                userRepository, dashboardService, consentService, mailsClient, "test-secret");
        job.generateBookingReminders();

        org.mockito.Mockito.verify(mailsClient, org.mockito.Mockito.never()).sendReminders(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reminderGenerationJob_tellsMailsAboutNextActions() {
        Journey journey = new Journey();
        journey.setUserId(userId);
        journey.setStatus(JourneyStatus.PREGNANCY);
        journeyRepository.save(journey);

        ReminderGenerationJob job = new ReminderGenerationJob(bookingRepository, journeyRepository,
                userRepository, dashboardService, consentService, mailsClient, "test-secret");
        job.generateJourneyReminders();

        org.mockito.ArgumentCaptor<java.util.List<DueReminder>> sent = org.mockito.ArgumentCaptor.captor();
        org.mockito.Mockito.verify(mailsClient).sendReminders(sent.capture());
        assertEquals("JOURNEY_NEXT_ACTION", sent.getValue().get(0).type());

        String firstStage = sent.getValue().get(0).dedupeKey();
        assertEquals(32, firstStage.length());
        assertFalse(firstStage.contains("PREGNANCY"));
        journey.setStatus(JourneyStatus.POSTPARTUM);
        org.mockito.Mockito.clearInvocations(mailsClient);
        journeyRepository.save(journey);
        job.generateJourneyReminders();
        org.mockito.Mockito.verify(mailsClient).sendReminders(sent.capture());
        assertNotEquals(firstStage, sent.getValue().get(0).dedupeKey());

        String secondStage = sent.getValue().get(0).dedupeKey();
        org.mockito.Mockito.clearInvocations(mailsClient);
        new ReminderGenerationJob(bookingRepository, journeyRepository, userRepository, dashboardService, consentService,
                mailsClient, "another-secret").generateJourneyReminders();
        org.mockito.Mockito.verify(mailsClient).sendReminders(sent.capture());
        assertNotEquals(secondStage, sent.getValue().get(0).dedupeKey(), "the key must depend on the secret");
    }

    @Test
    void paymentReconciliationJob_flagsStuckPaymentOnce_viaAudit() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String orderBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = Long.valueOf(orderBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        String paymentBody = mockMvc.perform(post("/api/v1/payments/create")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long paymentId = Long.valueOf(paymentBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
        payment.setCreatedAt(LocalDateTime.now().minusMinutes(45));
        paymentRepository.save(payment);

        PaymentReconciliationJob job = new PaymentReconciliationJob(paymentRepository, paymentReconciliationFlagRepository, auditService);
        job.flagStuckPayments();
        job.flagStuckPayments(); // re-run must not double-flag

        assertEquals(1, auditEventRepository.findAll().stream()
                .filter(e -> "PAYMENT_RECONCILIATION_STUCK".equals(e.getAction()))
                .count());
        assertTrue(paymentReconciliationFlagRepository.existsByPaymentId(paymentId));
    }
}
