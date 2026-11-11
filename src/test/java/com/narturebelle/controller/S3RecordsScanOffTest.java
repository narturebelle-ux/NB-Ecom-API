package com.narturebelle.controller;

import com.narturebelle.model.AuditEvent;
import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.AuditEventRepository;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.repository.HealthRecordRepository;
import com.narturebelle.repository.PendingUploadRepository;
import com.narturebelle.repository.RecordAccessEventRepository;
import com.narturebelle.repository.RecordShareRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import com.narturebelle.storage.S3StorageProvider;
import com.narturebelle.storage.SignedDownloadUrl;
import com.narturebelle.storage.SignedUploadUrl;
import com.narturebelle.storage.StoredObjectInfo;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pre-production without GuardDuty (owner, 11 Nov 2026): RECORDS_SCAN_REQUIRED=false makes new S3 uploads open at once. */
@SpringBootTest(properties = {
        "storage.provider=s3",
        "storage.s3.bucket=nb-files-preprod-test",
        "records.scan.required=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class S3RecordsScanOffTest {

    private static final String TOKEN = "scan-off-token";
    private static final String BUCKET = "nb-files-preprod-test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private HealthRecordRepository healthRecordRepository;
    @Autowired private PendingUploadRepository pendingUploadRepository;
    @Autowired private RecordShareRepository recordShareRepository;
    @Autowired private RecordAccessEventRepository recordAccessEventRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private S3StorageProvider s3;

    @BeforeEach
    void setUp() throws Exception {
        recordAccessEventRepository.deleteAll();
        recordShareRepository.deleteAll();
        healthRecordRepository.deleteAll();
        pendingUploadRepository.deleteAll();
        auditEventRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFirebaseUid("uid-scan-off");
        user.setEmail("scan-off@example.com");
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        Long id = userRepository.save(user).getId();
        Consent consent = new Consent();
        consent.setUserId(id);
        consent.setDocumentType(ConsentDocumentType.RECORDS_STORAGE);
        consent.setDocumentVersion(1);
        consentRepository.save(consent);
        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-scan-off", "scan-off@example.com", true, null, null));

        when(s3.bucket()).thenReturn(BUCKET);
        when(s3.createSignedUploadUrl(anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(new SignedUploadUrl("https://s3.test/put", null, LocalDateTime.now().plusMinutes(15)));
        when(s3.createSignedDownloadUrl(anyString(), anyString(), any(Duration.class), any(), any()))
                .thenReturn(new SignedDownloadUrl("https://s3.test/get", LocalDateTime.now().plusMinutes(10)));
    }

    @Test
    void newUpload_isNotScanned_availableAtOnce_andAuditSaysScanningIsOff() throws Exception {
        String start = mockMvc.perform(post("/api/v1/records/upload-url").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SCAN\",\"fileName\":\"f.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":1024}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String path = start.replaceAll(".*\"objectPath\":\"([^\"]+)\".*", "$1");
        when(s3.describe(BUCKET, path)).thenReturn(Optional.of(new StoredObjectInfo(1024, Instant.now())));

        String body = mockMvc.perform(post("/api/v1/records").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SCAN\",\"objectPath\":\"" + path + "\",\"originalFileName\":\"week-20 scan\","
                                + "\"mimeType\":\"application/pdf\",\"sizeBytes\":1024}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanStatus").value("NOT_SCANNED"))
                .andExpect(jsonPath("$.available").value(true))
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/v1/records/" + id + "/download-url").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("https://s3.test/get"));

        verify(s3, never()).readScanStatus(anyString(), anyString());
        AuditEvent uploaded = auditEventRepository.findAll().stream()
                .filter(a -> "RECORD_UPLOADED".equals(a.getAction())).findFirst().orElseThrow();
        assertTrue(uploaded.getMetadataJson().contains("\"malwareScanning\":\"OFF\""), uploaded.getMetadataJson());
        assertTrue(!uploaded.getMetadataJson().contains("week-20"), "no file details in the audit");
    }
}
