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
import com.narturebelle.model.ReminderSourceType;
import com.narturebelle.model.ReminderStatus;
import com.narturebelle.model.ReminderType;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.AuditEventRepository;
import com.narturebelle.repository.BookingRepository;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.repository.JourneyRepository;
import com.narturebelle.repository.PaymentRepository;
import com.narturebelle.repository.PaymentReconciliationFlagRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.ReminderRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.scheduling.PaymentReconciliationJob;
import com.narturebelle.scheduling.ReminderGenerationJob;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import com.narturebelle.service.ConsentService;
import com.narturebelle.service.DashboardService;
import com.narturebelle.service.NotificationPreferenceService;
import com.narturebelle.service.NotificationService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/** Covers Phase 5: reminder generation (idempotent), notification preferences (defaults + override),
 * the reminder list/dismiss endpoints, and the payment-reconciliation dedupe job. */
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
    @Autowired private ReminderRepository reminderRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationPreferenceService notificationPreferenceService;
    @Autowired private ConsentService consentService;
    @Autowired private DashboardService dashboardService;
    @Autowired private AuditService auditService;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUp() throws Exception {
        auditEventRepository.deleteAll();
        reminderRepository.deleteAll();
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
    void notificationPreferences_defaultInAppOnEverythingElseOff_andCanBeOverridden() throws Exception {
        mockMvc.perform(get("/api/v1/me/notification-preferences").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.channel=='IN_APP')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.channel=='EMAIL')].enabled").value(false));

        mockMvc.perform(put("/api/v1/me/notification-preferences/EMAIL")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        assertTrue(notificationPreferenceService.isEnabled(userId, com.narturebelle.model.NotificationChannelType.EMAIL));
    }

    @Test
    void notifyService_isIdempotent_forSameSource() {
        notificationService.notify(userId, ReminderType.BOOKING_UPCOMING, "Upcoming care session",
                "You have an upcoming care session.", ReminderSourceType.BOOKING, 999L, LocalDateTime.now());
        notificationService.notify(userId, ReminderType.BOOKING_UPCOMING, "Upcoming care session",
                "You have an upcoming care session.", ReminderSourceType.BOOKING, 999L, LocalDateTime.now());

        long count = reminderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, ReminderStatus.ACTIVE).stream()
                .filter(r -> r.getSourceType() == ReminderSourceType.BOOKING && r.getSourceId().equals(999L))
                .count();
        assertEquals(1, count);
    }

    @Test
    void reminderList_andDismiss_ownershipEnforced() throws Exception {
        notificationService.notify(userId, ReminderType.JOURNEY_NEXT_ACTION, "New recommended next steps",
                "Check your journey.", ReminderSourceType.JOURNEY, 1L, null);

        String body = mockMvc.perform(get("/api/v1/me/reminders").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        Long reminderId = Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/v1/me/reminders/" + reminderId + "/dismiss")
                        .header("Authorization", "Bearer " + TOKEN_OTHER))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/me/reminders/" + reminderId + "/dismiss")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));

        mockMvc.perform(get("/api/v1/me/reminders").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void reminderGenerationJob_createsBookingReminder_onlyOnce() {
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
                userRepository, dashboardService, consentService, notificationService);
        job.generateBookingReminders();
        job.generateBookingReminders(); // idempotent re-run

        long count = reminderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, ReminderStatus.ACTIVE).stream()
                .filter(r -> r.getType() == ReminderType.BOOKING_UPCOMING)
                .count();
        assertEquals(1, count);
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
                userRepository, dashboardService, consentService, notificationService);
        job.generateBookingReminders();

        assertTrue(reminderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(unconsentedUserId, ReminderStatus.ACTIVE).isEmpty());
    }

    @Test
    void reminderGenerationJob_createsJourneyReminder_whenNextActionsExist() {
        Journey journey = new Journey();
        journey.setUserId(userId);
        journey.setStatus(JourneyStatus.PREGNANCY);
        journeyRepository.save(journey);

        ReminderGenerationJob job = new ReminderGenerationJob(bookingRepository, journeyRepository,
                userRepository, dashboardService, consentService, notificationService);
        job.generateJourneyReminders();

        assertEquals(1, reminderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, ReminderStatus.ACTIVE).stream()
                .filter(r -> r.getType() == ReminderType.JOURNEY_NEXT_ACTION)
                .count());
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
