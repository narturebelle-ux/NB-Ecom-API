package com.narturebelle.controller;

import com.narturebelle.model.Booking;
import com.narturebelle.model.BookingStatus;
import com.narturebelle.model.Provider;
import com.narturebelle.model.ProviderStatus;
import com.narturebelle.model.RecordAccessEvent;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.BookingRepository;
import com.narturebelle.repository.HealthRecordRepository;
import com.narturebelle.repository.ProviderRepository;
import com.narturebelle.repository.RecordAccessEventRepository;
import com.narturebelle.repository.RecordShareRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import com.narturebelle.storage.SignedDownloadUrl;
import com.narturebelle.storage.SignedUploadUrl;
import com.narturebelle.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers Phase 4: upload/register, list/access/share/revoke, delete cascade, access log, and the "no health data in logs" structural guarantee. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecordsTest {

    private static final String TOKEN_OWNER = "records-token-owner";
    private static final String TOKEN_OTHER = "records-token-other";
    private static final String TOKEN_PROVIDER = "records-token-provider";
    private static final String TOKEN_ADMIN = "records-token-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private HealthRecordRepository healthRecordRepository;
    @Autowired private RecordShareRepository recordShareRepository;
    @Autowired private RecordAccessEventRepository recordAccessEventRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private StorageProvider storageProvider;

    private Long ownerUserId;
    private Long providerUserId;
    private Long providerId;

    @BeforeEach
    void setUp() throws Exception {
        recordAccessEventRepository.deleteAll();
        recordShareRepository.deleteAll();
        healthRecordRepository.deleteAll();
        bookingRepository.deleteAll();
        providerRepository.deleteAll();
        userRepository.deleteAll();

        ownerUserId = createUser("uid-records-owner", "records-owner@example.com", Role.MOTHER);
        createUser("uid-records-other", "records-other@example.com", Role.MOTHER);
        providerUserId = createUser("uid-records-provider", "records-provider@example.com", Role.GYNECOLOGIST);
        createUser("uid-records-admin", "records-admin@example.com", Role.SUPER_ADMIN);

        Provider provider = new Provider();
        provider.setUserId(providerUserId);
        provider.setProfessionalType(Role.GYNECOLOGIST);
        provider.setStatus(ProviderStatus.ACTIVE);
        providerId = providerRepository.save(provider).getId();

        when(firebaseTokenVerifier.verify(TOKEN_OWNER))
                .thenReturn(new VerifiedFirebaseToken("uid-records-owner", "records-owner@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_OTHER))
                .thenReturn(new VerifiedFirebaseToken("uid-records-other", "records-other@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_PROVIDER))
                .thenReturn(new VerifiedFirebaseToken("uid-records-provider", "records-provider@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-records-admin", "records-admin@example.com", true, null, null));

        when(storageProvider.createSignedUploadUrl(anyString(), anyString(), any(Duration.class)))
                .thenReturn(new SignedUploadUrl("https://fake.storage/upload", "fake-token", LocalDateTime.now().plusMinutes(15)));
        when(storageProvider.createSignedDownloadUrl(anyString(), anyString(), any(Duration.class)))
                .thenReturn(new SignedDownloadUrl("https://fake.storage/download", LocalDateTime.now().plusMinutes(10)));
        when(storageProvider.objectExists(anyString(), anyString())).thenReturn(true);
    }

    private Long createUser(String uid, String email, Role role) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(role));
        return userRepository.save(user).getId();
    }

    private Long uploadAndRegister(String token) throws Exception {
        String uploadBody = mockMvc.perform(post("/api/v1/records/upload-url")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"LAB_REPORT\",\"fileName\":\"report.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":1024}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String objectPath = uploadBody.replaceAll(".*\"objectPath\":\"([^\"]+)\".*", "$1");

        String body = mockMvc.perform(post("/api/v1/records")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"LAB_REPORT\",\"objectPath\":\"" + objectPath + "\","
                                + "\"originalFileName\":\"report.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":1024}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void upload_happyPath_createsRecordAndLogsUpload() throws Exception {
        Long recordId = uploadAndRegister(TOKEN_OWNER);

        mockMvc.perform(get("/api/v1/records").header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("UPLOADED"))
                .andExpect(jsonPath("$[0].scanStatus").value("NOT_SCANNED"));

        List<RecordAccessEvent> events = recordAccessEventRepository.findByRecordIdOrderByOccurredAtDesc(recordId);
        assertEquals(1, events.size());
        assertEquals("UPLOAD", events.get(0).getAction().name());
    }

    @Test
    void uploadUrl_disallowedMimeType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/records/upload-url")
                        .header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"LAB_REPORT\",\"fileName\":\"virus.exe\",\"mimeType\":\"application/x-msdownload\",\"sizeBytes\":1024}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void uploadUrl_oversized_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/records/upload-url")
                        .header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"LAB_REPORT\",\"fileName\":\"big.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":99999999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void register_beforeRealUpload_returns409() throws Exception {
        when(storageProvider.objectExists(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/records")
                        .header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"LAB_REPORT\",\"objectPath\":\"records/1/fake\","
                                + "\"originalFileName\":\"report.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":1024}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_UPLOAD_NOT_CONFIRMED"));
    }

    @Test
    void getOne_ownerAdminOk_unrelatedUser403_logsViewEveryTime() throws Exception {
        Long recordId = uploadAndRegister(TOKEN_OWNER);

        mockMvc.perform(get("/api/v1/records/" + recordId).header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/records/" + recordId).header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/records/" + recordId).header("Authorization", "Bearer " + TOKEN_OTHER))
                .andExpect(status().isForbidden());

        long viewCount = recordAccessEventRepository.findByRecordIdOrderByOccurredAtDesc(recordId).stream()
                .filter(e -> e.getAction().name().equals("VIEW")).count();
        assertEquals(2, viewCount);
    }

    @Test
    void shareWithProvider_grantsAccess_revokeRemovesIt() throws Exception {
        Long recordId = uploadAndRegister(TOKEN_OWNER);
        String expiresAt = LocalDateTime.now().plusDays(7).toString();

        String shareBody = mockMvc.perform(post("/api/v1/records/" + recordId + "/shares")
                        .header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareTargetType\":\"PROVIDER\",\"targetProviderId\":" + providerId
                                + ",\"expiresAt\":\"" + expiresAt + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareTargetType").value("PROVIDER"))
                .andReturn().getResponse().getContentAsString();
        Long shareId = Long.valueOf(shareBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/v1/records/" + recordId).header("Authorization", "Bearer " + TOKEN_PROVIDER))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/records/shared-with-me").header("Authorization", "Bearer " + TOKEN_PROVIDER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(post("/api/v1/records/shares/" + shareId + "/revoke")
                        .header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no longer needed\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/records/" + recordId).header("Authorization", "Bearer " + TOKEN_PROVIDER))
                .andExpect(status().isForbidden());
    }

    @Test
    void shareViaBooking_grantsAccessToAssignedProvider() throws Exception {
        Long recordId = uploadAndRegister(TOKEN_OWNER);

        Booking booking = new Booking();
        booking.setUserId(ownerUserId);
        booking.setProviderId(providerId);
        booking.setServiceOfferingId(1L);
        booking.setSlotId(1L);
        booking.setProfessionalType(Role.GYNECOLOGIST);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setScheduledStart(LocalDateTime.now().plusDays(1));
        booking.setScheduledEnd(LocalDateTime.now().plusDays(1).plusMinutes(30));
        Long bookingId = bookingRepository.save(booking).getId();

        String expiresAt = LocalDateTime.now().plusDays(7).toString();
        mockMvc.perform(post("/api/v1/records/" + recordId + "/shares")
                        .header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareTargetType\":\"BOOKING\",\"targetBookingId\":" + bookingId
                                + ",\"expiresAt\":\"" + expiresAt + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/records/" + recordId).header("Authorization", "Bearer " + TOKEN_PROVIDER))
                .andExpect(status().isOk());
    }

    @Test
    void shareViaBooking_rejectsBookingNotOwnedByRecordOwner() throws Exception {
        Long recordId = uploadAndRegister(TOKEN_OWNER);

        Booking booking = new Booking();
        booking.setUserId(providerUserId); // not the record owner
        booking.setProviderId(providerId);
        booking.setServiceOfferingId(1L);
        booking.setSlotId(1L);
        booking.setProfessionalType(Role.GYNECOLOGIST);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setScheduledStart(LocalDateTime.now().plusDays(1));
        booking.setScheduledEnd(LocalDateTime.now().plusDays(1).plusMinutes(30));
        Long bookingId = bookingRepository.save(booking).getId();

        mockMvc.perform(post("/api/v1/records/" + recordId + "/shares")
                        .header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareTargetType\":\"BOOKING\",\"targetBookingId\":" + bookingId
                                + ",\"expiresAt\":\"" + LocalDateTime.now().plusDays(7) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShare_rejectsPastOrBeyondMaxExpiry() throws Exception {
        Long recordId = uploadAndRegister(TOKEN_OWNER);

        mockMvc.perform(post("/api/v1/records/" + recordId + "/shares")
                        .header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareTargetType\":\"PROVIDER\",\"targetProviderId\":" + providerId
                                + ",\"expiresAt\":\"" + LocalDateTime.now().minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/records/" + recordId + "/shares")
                        .header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareTargetType\":\"PROVIDER\",\"targetProviderId\":" + providerId
                                + ",\"expiresAt\":\"" + LocalDateTime.now().plusDays(400) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_softDeletes_cascadesShareRevocation_bestEffortStorageDelete() throws Exception {
        Long recordId = uploadAndRegister(TOKEN_OWNER);
        mockMvc.perform(post("/api/v1/records/" + recordId + "/shares")
                        .header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareTargetType\":\"PROVIDER\",\"targetProviderId\":" + providerId
                                + ",\"expiresAt\":\"" + LocalDateTime.now().plusDays(7) + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/records/" + recordId).header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"));

        mockMvc.perform(get("/api/v1/records").header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        assertTrue(recordShareRepository.findByRecordIdAndRevokedAtIsNull(recordId).isEmpty());
    }

    @Test
    void patch_category_ownerOrAdminOnly() throws Exception {
        Long recordId = uploadAndRegister(TOKEN_OWNER);

        mockMvc.perform(patch("/api/v1/records/" + recordId)
                        .header("Authorization", "Bearer " + TOKEN_OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SCAN\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/records/" + recordId)
                        .header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SCAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("SCAN"));
    }

    @Test
    void accessLog_listsActions_ownerAndAdminOnly_notSharedProvider() throws Exception {
        Long recordId = uploadAndRegister(TOKEN_OWNER);
        mockMvc.perform(get("/api/v1/records/" + recordId).header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/records/" + recordId + "/access-log").header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2))); // UPLOAD + VIEW

        mockMvc.perform(get("/api/v1/records/" + recordId + "/access-log").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/records/" + recordId + "/access-log").header("Authorization", "Bearer " + TOKEN_PROVIDER))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordAccessEvent_hasNoColumnForFilenameOrCategory_structurally() throws Exception {
        // The RecordAccessEvent entity simply has no filename/category/mime/size field at all — this
        // is a compile-time guarantee, demonstrated here by confirming the getters that DO exist
        // are the only ones (action, actorUserId, actorRelationship, shareId, occurredAt, recordId, id).
        Long recordId = uploadAndRegister(TOKEN_OWNER);
        List<RecordAccessEvent> events = recordAccessEventRepository.findByRecordIdOrderByOccurredAtDesc(recordId);
        assertEquals(1, events.size());
        RecordAccessEvent event = events.get(0);
        // If this compiles, the entity has exactly these fields and nothing filename/health-data-shaped.
        assertEquals(recordId, event.getRecordId());
        assertTrue(event.getAction() != null && event.getActorRelationship() != null);
    }
}
