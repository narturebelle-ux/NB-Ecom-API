package com.narturebelle.controller;

import com.narturebelle.model.Entitlement;
import com.narturebelle.model.Modality;
import com.narturebelle.model.Provider;
import com.narturebelle.model.ProviderStatus;
import com.narturebelle.model.ProviderSlot;
import com.narturebelle.model.Role;
import com.narturebelle.model.ServiceOffering;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.model.User;
import com.narturebelle.repository.BookingNoteAmendmentRepository;
import com.narturebelle.repository.BookingRepository;
import com.narturebelle.repository.EntitlementRepository;
import com.narturebelle.repository.PayoutRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.ProviderSlotRepository;
import com.narturebelle.repository.ServiceOfferingRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 3.2: booking creation/race, cancel/reschedule, session lifecycle, entitlement consumption, payouts, feedback. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingTest {

    private static final String TOKEN_USER = "booking-token-user";
    private static final String TOKEN_OTHER_USER = "booking-token-other-user";
    private static final String TOKEN_PROVIDER = "booking-token-provider";
    private static final String TOKEN_ADMIN = "booking-token-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceOfferingRepository serviceOfferingRepository;
    @Autowired private ProviderSlotRepository providerSlotRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingNoteAmendmentRepository bookingNoteAmendmentRepository;
    @Autowired private EntitlementRepository entitlementRepository;
    @Autowired private PayoutRepository payoutRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long userId;
    private Long otherUserId;
    private Long providerUserId;
    private Long providerId;
    private Long serviceOfferingId;

    @BeforeEach
    void setUp() throws Exception {
        payoutRepository.deleteAll();
        bookingNoteAmendmentRepository.deleteAll();
        bookingRepository.deleteAll();
        entitlementRepository.deleteAll();
        providerSlotRepository.deleteAll();
        serviceOfferingRepository.deleteAll();
        providerRepository.deleteAll();
        userRepository.deleteAll();

        userId = createUser("uid-booking-user", "booking-user@example.com", Role.MOTHER);
        otherUserId = createUser("uid-booking-other", "booking-other@example.com", Role.MOTHER);
        providerUserId = createUser("uid-booking-provideruser", "booking-provideruser@example.com", Role.GYNECOLOGIST);
        createUser("uid-booking-admin", "booking-admin@example.com", Role.SUPER_ADMIN);

        Provider provider = new Provider();
        provider.setUserId(providerUserId);
        provider.setProfessionalType(Role.GYNECOLOGIST);
        provider.setDisplayName("Dr. Booking");
        provider.setStatus(ProviderStatus.ACTIVE);
        providerId = providerRepository.save(provider).getId();

        ServiceOffering offering = new ServiceOffering();
        offering.setProviderId(providerId);
        offering.setName("Consultation");
        offering.setModality(Modality.PHYSICAL);
        offering.setDurationMinutes(30);
        offering.setActive(true);
        serviceOfferingId = serviceOfferingRepository.save(offering).getId();

        grantEntitlement(userId, Role.GYNECOLOGIST, 1);

        when(firebaseTokenVerifier.verify(TOKEN_USER))
                .thenReturn(new VerifiedFirebaseToken("uid-booking-user", "booking-user@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_OTHER_USER))
                .thenReturn(new VerifiedFirebaseToken("uid-booking-other", "booking-other@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_PROVIDER))
                .thenReturn(new VerifiedFirebaseToken("uid-booking-provideruser", "booking-provideruser@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-booking-admin", "booking-admin@example.com", true, null, null));
    }

    private Long createUser(String uid, String email, Role role) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(role));
        return userRepository.save(user).getId();
    }

    private void grantEntitlement(Long userId, Role type, int totalSessions) {
        Entitlement entitlement = new Entitlement();
        entitlement.setUserId(userId);
        // Fake, unique-per-user orderId — entitlements(order_id, professional_type) is unique, and
        // these tests never go through the real Phase 2 order flow, so a negative placeholder (never
        // collides with a real Order id) keeps each test user's fixture distinct.
        entitlement.setOrderId(-userId);
        entitlement.setProfessionalType(type);
        entitlement.setTotalSessions(totalSessions);
        entitlement.setUsedSessions(0);
        entitlement.setExpiresAt(LocalDateTime.now().plusDays(30));
        entitlementRepository.save(entitlement);
    }

    private Long createOpenSlot() {
        ProviderSlot slot = new ProviderSlot();
        slot.setProviderId(providerId);
        slot.setStartAt(LocalDateTime.now().plusDays(1));
        slot.setEndAt(LocalDateTime.now().plusDays(1).plusMinutes(30));
        slot.setStatus(SlotStatus.OPEN);
        return providerSlotRepository.save(slot).getId();
    }

    private Long createBooking() throws Exception {
        Long slotId = createOpenSlot();
        String body = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":" + providerId + ",\"serviceOfferingId\":" + serviceOfferingId
                                + ",\"slotId\":" + slotId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void create_happyPath_bookedAndSlotFlips_entitlementUntouched() throws Exception {
        Long slotId = createOpenSlot();
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":" + providerId + ",\"serviceOfferingId\":" + serviceOfferingId
                                + ",\"slotId\":" + slotId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.professionalType").value("GYNECOLOGIST"));

        assertEquals(SlotStatus.BOOKED, providerSlotRepository.findById(slotId).orElseThrow().getStatus());
        assertEquals(0, entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0).getUsedSessions());
    }

    @Test
    void create_nonBookableProvider_returns409() throws Exception {
        Provider invited = new Provider();
        invited.setUserId(otherUserId);
        invited.setProfessionalType(Role.NUTRITIONIST);
        invited.setStatus(ProviderStatus.INVITED);
        Long invitedProviderId = providerRepository.save(invited).getId();

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":" + invitedProviderId + ",\"serviceOfferingId\":" + serviceOfferingId
                                + ",\"slotId\":" + createOpenSlot() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROVIDER_NOT_BOOKABLE"));
    }

    @Test
    void create_noEntitlement_returns409() throws Exception {
        Long slotId = createOpenSlot();
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + TOKEN_OTHER_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":" + providerId + ",\"serviceOfferingId\":" + serviceOfferingId
                                + ",\"slotId\":" + slotId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ENTITLEMENT_AVAILABLE"));
    }

    @Test
    void create_slotAlreadyTaken_returns409() throws Exception {
        Long slotId = createOpenSlot();
        grantEntitlement(otherUserId, Role.GYNECOLOGIST, 1);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":" + providerId + ",\"serviceOfferingId\":" + serviceOfferingId
                                + ",\"slotId\":" + slotId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + TOKEN_OTHER_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":" + providerId + ",\"serviceOfferingId\":" + serviceOfferingId
                                + ",\"slotId\":" + slotId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
    }

    @Test
    void cancel_byOwnerProviderOrAdmin_freesSlot_unrelatedUser403() throws Exception {
        Long bookingId = createBooking();
        Long slotId = bookingRepository.findById(bookingId).orElseThrow().getSlotId();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + TOKEN_OTHER_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"not mine\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"change of plans\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(SlotStatus.OPEN, providerSlotRepository.findById(slotId).orElseThrow().getStatus());
    }

    @Test
    void cancel_byAssignedProvider_succeeds() throws Exception {
        Long bookingId = createBooking();
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + TOKEN_PROVIDER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"provider unavailable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void reschedule_oldRescheduled_notCancelled_newBookingReferencesOld() throws Exception {
        Long bookingId = createBooking();
        Long newSlotId = createOpenSlot();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reschedule")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newSlotId\":" + newSlotId + ",\"reason\":\"conflict\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.rescheduledFromBookingId").value(bookingId));

        assertEquals("RESCHEDULED", bookingRepository.findById(bookingId).orElseThrow().getStatus().name());
    }

    @Test
    void start_requiresBooked_rejectedForBookingOwner() throws Exception {
        Long bookingId = createBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/start")
                        .header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/start")
                        .header("Authorization", "Bearer " + TOKEN_PROVIDER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void complete_withoutNote_returns400() throws Exception {
        Long bookingId = createBooking();
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/complete")
                        .header("Authorization", "Bearer " + TOKEN_PROVIDER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void complete_consumesEntitlementOnlyOnCompletion_createsPayout_lockNotesAndSecondCompleteConflicts() throws Exception {
        Long bookingId = createBooking();
        assertEquals(0, entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0).getUsedSessions());

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/complete")
                        .header("Authorization", "Bearer " + TOKEN_PROVIDER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Discussed nutrition plan.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertEquals(1, entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0).getUsedSessions());
        assertEquals(1, payoutRepository.findByProviderId(providerId).size());
        assertEquals(new java.math.BigDecimal("500.00"), payoutRepository.findByProviderId(providerId).get(0).getAmount());

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/complete")
                        .header("Authorization", "Bearer " + TOKEN_PROVIDER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_ALLOWED"));
    }

    @Test
    void amendNote_afterCompletion_writesAmendmentAndAudit() throws Exception {
        Long bookingId = createBooking();
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/complete")
                        .header("Authorization", "Bearer " + TOKEN_PROVIDER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Initial note.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/note")
                        .header("Authorization", "Bearer " + TOKEN_PROVIDER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Corrected note.\",\"reason\":\"typo fix\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerNote").value("Corrected note."));

        assertEquals(1, bookingNoteAmendmentRepository.findByBookingIdOrderByAmendedAtDesc(bookingId).size());
    }

    @Test
    void noShow_eitherParty_consumesNothingAndPaysNothing_userCannotReportOwnNoShow() throws Exception {
        Long bookingId = createBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/no-show")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"party\":\"USER\",\"reason\":\"self-report\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/no-show")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"party\":\"PROVIDER\",\"reason\":\"never joined\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW_PROVIDER"));

        assertEquals(0, entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0).getUsedSessions());
        assertTrue(payoutRepository.findByProviderId(providerId).isEmpty());
    }

    @Test
    void feedback_onlyAfterCompletion_onlyOwner_onlyOnce() throws Exception {
        Long bookingId = createBooking();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/feedback")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_ALLOWED"));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/complete")
                        .header("Authorization", "Bearer " + TOKEN_PROVIDER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"done\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/feedback")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Great!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/feedback")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_ALLOWED"));
    }

    @Test
    void getOne_threeWayOwnership() throws Exception {
        Long bookingId = createBooking();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", "Bearer " + TOKEN_PROVIDER))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", "Bearer " + TOKEN_OTHER_USER))
                .andExpect(status().isForbidden());
    }
}
