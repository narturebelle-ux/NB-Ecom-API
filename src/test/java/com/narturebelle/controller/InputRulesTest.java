package com.narturebelle.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.model.*;
import com.narturebelle.repository.*;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The logical checks: due dates, phone numbers, slots, credentials, and what a booking is cross-checked against. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InputRulesTest {

    private static final String MOM = "Bearer rules-mom";
    private static final String ADMIN = "Bearer rules-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EntitlementRepository entitlementRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceOfferingRepository serviceOfferingRepository;
    @Autowired private ProviderSlotRepository providerSlotRepository;
    @Autowired private ProviderCredentialRepository providerCredentialRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long momId;

    @BeforeEach
    void setUp() throws Exception {
        bookingRepository.deleteAll();
        entitlementRepository.deleteAll();
        providerSlotRepository.deleteAll();
        providerCredentialRepository.deleteAll();
        serviceOfferingRepository.deleteAll();
        providerRepository.deleteAll();
        journeyRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        momId = user("uid-rules-mom", "rules-mom@example.com", Role.MOTHER);
        user("uid-rules-admin", "rules-admin@example.com", Role.SUPER_ADMIN);
        when(firebaseTokenVerifier.verify("rules-mom"))
                .thenReturn(new VerifiedFirebaseToken("uid-rules-mom", "rules-mom@example.com", true, null, null));
        when(firebaseTokenVerifier.verify("rules-admin"))
                .thenReturn(new VerifiedFirebaseToken("uid-rules-admin", "rules-admin@example.com", true, null, null));
    }

    private Long user(String uid, String email, Role role) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(role));
        Long id = userRepository.save(user).getId();
        for (ConsentDocumentType type : Set.of(ConsentDocumentType.TERMS, ConsentDocumentType.PRIVACY)) {
            Consent consent = new Consent();
            consent.setUserId(id);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }
        return id;
    }

    private void journey(JourneyStatus status, LocalDate edd) {
        Journey journey = new Journey();
        journey.setUserId(momId);
        journey.setStatus(status);
        journey.setExpectedDeliveryDate(edd);
        journey.setStartedAt(LocalDateTime.now());
        journeyRepository.save(journey);
    }

    private long provider(Role type) {
        Provider provider = new Provider();
        provider.setUserId(user("uid-rules-" + type, "rules-" + type + "@example.com", type));
        provider.setProfessionalType(type);
        provider.setDisplayName("Dr. Rules");
        provider.setStatus(ProviderStatus.ACTIVE);
        return providerRepository.save(provider).getId();
    }

    private long offering(long providerId) {
        ServiceOffering offering = new ServiceOffering();
        offering.setProviderId(providerId);
        offering.setName("Consultation");
        offering.setModality(Modality.VIRTUAL);
        offering.setDurationMinutes(30);
        offering.setActive(true);
        return serviceOfferingRepository.save(offering).getId();
    }

    private long slot(long providerId, LocalDateTime start) {
        ProviderSlot slot = new ProviderSlot();
        slot.setProviderId(providerId);
        slot.setStartAt(start);
        slot.setEndAt(start.plusMinutes(30));
        slot.setStatus(SlotStatus.OPEN);
        return providerSlotRepository.save(slot).getId();
    }

    private void entitlement(Role type, LocalDateTime expiresAt) {
        Entitlement entitlement = new Entitlement();
        entitlement.setUserId(momId);
        entitlement.setOrderId(-momId);
        entitlement.setProfessionalType(type);
        entitlement.setTotalSessions(2);
        entitlement.setUsedSessions(0);
        entitlement.setExpiresAt(expiresAt);
        entitlementRepository.save(entitlement);
    }

    private ResultActions book(long providerId, long slotId) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings").header("Authorization", MOM).contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":" + providerId + ",\"serviceOfferingId\":" + offering(providerId) + ",\"slotId\":" + slotId + "}"));
    }

    private ResultActions createJourney(String status, LocalDate edd) throws Exception {
        String date = edd == null ? "" : ",\"expectedDeliveryDate\":\"" + edd + "\"";
        return mockMvc.perform(post("/api/v1/me/journeys").header("Authorization", MOM).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"" + date + "}"));
    }

    @Test
    void dueDates() throws Exception {
        LocalDate today = LocalDate.now();
        createJourney("PREGNANCY", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.expectedDeliveryDate").exists());
        createJourney("PREGNANCY", today.minusDays(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.expectedDeliveryDate").value("Due date can't be in the past."));
        createJourney("PREGNANCY", today.plusWeeks(43)).andExpect(status().isBadRequest());
        createJourney("POSTPARTUM", today.plusDays(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.expectedDeliveryDate").value("Delivery date can't be in the future."));
        createJourney("POSTPARTUM", today.minusDays(10)).andExpect(status().isOk());
    }

    @Test
    void phoneNumbers() throws Exception {
        for (String bad : new String[]{"12345", "5845012345", "+1 415 555 0100", "not a phone"}) {
            mockMvc.perform(patch("/api/v1/me").header("Authorization", MOM).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.phone").exists());
        }
        mockMvc.perform(patch("/api/v1/me").header("Authorization", MOM).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+91 98450 12345\"}"))
                .andExpect(status().isOk());
        assertEquals("+919845012345", userRepository.findById(momId).orElseThrow().getPhone());
    }

    @Test
    void slotsMustMakeSense() throws Exception {
        long providerId = provider(Role.GYNECOLOGIST);
        LocalDateTime start = LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.MINUTES);
        String url = "/api/v1/admin/providers/" + providerId + "/slots";

        for (String body : new String[]{
                window(start, start.minusMinutes(30)),                                  // ends before it starts
                window(start.minusDays(5), start.minusDays(5).plusMinutes(30)),         // in the past
                window(start, start.plusHours(9)),                                      // too long
                window(start, start.plusMinutes(30)) + "," + window(start.plusMinutes(15), start.plusMinutes(45)) // overlap each other
        }) {
            mockMvc.perform(post(url).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"slots\":[" + body + "]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.slots").exists());
        }
        String good = "{\"slots\":[" + window(start, start.plusMinutes(30)) + "]}";
        mockMvc.perform(post(url).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(good))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post(url).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(good))
                .andExpect(status().isBadRequest()); // overlaps what's now saved
    }

    private static String window(LocalDateTime start, LocalDateTime end) {
        return "{\"startAt\":\"" + start + "\",\"endAt\":\"" + end + "\"}";
    }

    @Test
    void credentialDates() throws Exception {
        String url = "/api/v1/admin/providers/" + provider(Role.NUTRITIONIST) + "/credentials";
        LocalDate today = LocalDate.now();
        mockMvc.perform(post(url).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialType\":\"MCI\",\"issuedAt\":\"" + today.plusDays(1) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.issuedAt").exists());
        mockMvc.perform(post(url).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialType\":\"MCI\",\"expiresAt\":\"" + today.minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.expiresAt").exists());
        mockMvc.perform(post(url).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialType\":\"MCI\",\"issuedAt\":\"" + today.minusYears(1) + "\",\"expiresAt\":\"" + today.plusYears(1) + "\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void bookingsAreCrossCheckedAgainstHerPlanAndStage() throws Exception {
        LocalDate due = LocalDate.now().plusDays(60);
        journey(JourneyStatus.PREGNANCY, due);
        LocalDateTime planEnds = LocalDateTime.now().plusDays(120);

        long gyn = provider(Role.GYNECOLOGIST);
        entitlement(Role.GYNECOLOGIST, planEnds);
        book(gyn, slot(gyn, LocalDateTime.now().minusHours(1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_IN_PAST"));
        book(gyn, slot(gyn, planEnds.plusDays(1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_AFTER_PLAN_EXPIRY"));

        // Inside her third trimester: no note.
        book(gyn, slot(gyn, LocalDateTime.now().plusDays(10)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timingNote").doesNotExist());

        long pediatrician = provider(Role.PEDIATRICIAN);
        Entitlement pediatric = new Entitlement();
        pediatric.setUserId(momId);
        pediatric.setOrderId(-momId - 1);
        pediatric.setProfessionalType(Role.PEDIATRICIAN);
        pediatric.setTotalSessions(1);
        pediatric.setUsedSessions(0);
        pediatric.setExpiresAt(planEnds);
        entitlementRepository.save(pediatric);
        book(pediatrician, slot(pediatrician, LocalDateTime.now().plusDays(5)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_NOT_FOR_STAGE"));
        book(pediatrician, slot(pediatrician, due.plusDays(3).atTime(10, 0)))
                .andExpect(status().isOk());
    }

    @Test
    void aBookingOutsideTheSuggestedRange_isAllowedWithANote() throws Exception {
        journey(JourneyStatus.PREGNANCY, LocalDate.now().plusDays(200)); // she's in her first or second trimester
        long lactation = provider(Role.LACTATION_CONSULTANT);
        entitlement(Role.LACTATION_CONSULTANT, LocalDateTime.now().plusDays(365));
        book(lactation, slot(lactation, LocalDateTime.now().plusDays(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timingNote").value(org.hamcrest.Matchers.containsString("outside the usual time")));
    }

    @Test
    void cancelOrRescheduleAnyTimeBeforeTheSessionStarts() throws Exception {
        long gyn = provider(Role.GYNECOLOGIST);
        entitlement(Role.GYNECOLOGIST, LocalDateTime.now().plusDays(365));
        String created = book(gyn, slot(gyn, LocalDateTime.now().plusMinutes(10)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(created).get("id").asLong();

        // Ten minutes before is still fine; too long a reason is a 400, not a crash.
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel").header("Authorization", MOM)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.reason").exists());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel").header("Authorization", MOM)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Change of plans\"}"))
                .andExpect(status().isOk());

        Booking started = new Booking();
        started.setUserId(momId);
        started.setProviderId(gyn);
        started.setServiceOfferingId(offering(gyn));
        started.setSlotId(slot(gyn, LocalDateTime.now().minusMinutes(5)));
        started.setProfessionalType(Role.GYNECOLOGIST);
        started.setStatus(BookingStatus.BOOKED);
        started.setScheduledStart(LocalDateTime.now().minusMinutes(5));
        started.setScheduledEnd(LocalDateTime.now().plusMinutes(25));
        long startedId = bookingRepository.save(started).getId();
        mockMvc.perform(post("/api/v1/bookings/" + startedId + "/cancel").header("Authorization", MOM)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Too late\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOKING_ALREADY_STARTED"));
    }

    @Test
    void sessionWindows_comeFromHerDueDate() throws Exception {
        mockMvc.perform(get("/api/v1/me/care/session-windows").header("Authorization", MOM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").doesNotExist())
                .andExpect(jsonPath("$.windows.length()").value(0));

        LocalDate today = LocalDate.now();
        LocalDate due = today.plusDays(70); // 10 weeks left: she's in the third trimester
        journey(JourneyStatus.PREGNANCY, due);
        String body = mockMvc.perform(get("/api/v1/me/care/session-windows").header("Authorization", MOM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(due.toString()))
                .andExpect(jsonPath("$.weeksRemaining").value(10))
                .andExpect(jsonPath("$.guidanceOnly").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode windows = objectMapper.readTree(body).get("windows");
        assertTrue(windows.size() > 0);
        boolean gynNow = false;
        boolean pediatricianAfterBirth = false;
        for (JsonNode w : windows) {
            LocalDate from = LocalDate.parse(w.get("from").asText());
            LocalDate to = LocalDate.parse(w.get("to").asText());
            assertFalse(from.isBefore(today), "finished ranges are dropped, and none start before today");
            assertFalse(to.isBefore(from));
            if (w.get("professionalType").asText().equals("GYNECOLOGIST") && w.get("current").asBoolean()) gynNow = true;
            if (w.get("professionalType").asText().equals("PEDIATRICIAN") && !from.isBefore(due)) pediatricianAfterBirth = true;
        }
        assertTrue(gynNow);
        assertTrue(pediatricianAfterBirth);
    }
}
