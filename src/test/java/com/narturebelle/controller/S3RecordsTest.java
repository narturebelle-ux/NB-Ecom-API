package com.narturebelle.controller;

import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.Role;
import com.narturebelle.model.ScanVerdict;
import com.narturebelle.model.User;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F9-S19 with STORAGE_PROVIDER=s3: random keys, scan gating, multipart videos, retention dates, quota, restore. */
@SpringBootTest(properties = {
        "storage.provider=s3",
        "storage.s3.bucket=nb-files-test",
        "records.upload.quota-bytes-per-user=200000000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class S3RecordsTest {

    private static final String TOKEN_OWNER = "s3-token-owner";
    private static final String TOKEN_OTHER = "s3-token-other";
    private static final String TOKEN_ADMIN = "s3-token-admin";
    private static final String BUCKET = "nb-files-test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private HealthRecordRepository healthRecordRepository;
    @Autowired private PendingUploadRepository pendingUploadRepository;
    @Autowired private RecordShareRepository recordShareRepository;
    @Autowired private RecordAccessEventRepository recordAccessEventRepository;
    @Autowired private ConsentRepository consentRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private S3StorageProvider s3;

    @BeforeEach
    void setUp() throws Exception {
        recordAccessEventRepository.deleteAll();
        recordShareRepository.deleteAll();
        healthRecordRepository.deleteAll();
        pendingUploadRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        createUser("uid-s3-owner", "s3-owner@example.com", Role.MOTHER);
        createUser("uid-s3-other", "s3-other@example.com", Role.MOTHER);
        createUser("uid-s3-admin", "s3-admin@example.com", Role.SUPER_ADMIN);
        when(firebaseTokenVerifier.verify(TOKEN_OWNER))
                .thenReturn(new VerifiedFirebaseToken("uid-s3-owner", "s3-owner@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_OTHER))
                .thenReturn(new VerifiedFirebaseToken("uid-s3-other", "s3-other@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-s3-admin", "s3-admin@example.com", true, null, null));

        when(s3.bucket()).thenReturn(BUCKET);
        when(s3.createSignedUploadUrl(anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(new SignedUploadUrl("https://s3.test/put", null, LocalDateTime.now().plusMinutes(15)));
        when(s3.createSignedDownloadUrl(anyString(), anyString(), any(Duration.class), any(), any()))
                .thenReturn(new SignedDownloadUrl("https://s3.test/get", LocalDateTime.now().plusMinutes(10)));
        when(s3.presignUploadPart(anyString(), anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenAnswer(inv -> new SignedUploadUrl("https://s3.test/part/" + inv.getArgument(3), null, LocalDateTime.now().plusMinutes(15)));
        when(s3.startMultipartUpload(anyString(), anyString(), anyString())).thenReturn("upload-1");
        when(s3.readScanStatus(anyString(), anyString())).thenReturn(ScanVerdict.PENDING);
    }

    private void createUser(String uid, String email, Role role) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRoles(Set.of(role));
        Long id = userRepository.save(user).getId();
        Consent consent = new Consent();
        consent.setUserId(id);
        consent.setDocumentType(ConsentDocumentType.RECORDS_STORAGE);
        consent.setDocumentVersion(1);
        consentRepository.save(consent);
    }

    private String uploadUrl(String token, String mime, long size) throws Exception {
        return mockMvc.perform(post("/api/v1/records/upload-url").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SCAN\",\"fileName\":\"f\",\"mimeType\":\"" + mime + "\",\"sizeBytes\":" + size + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String field(String json, String name) {
        return json.replaceAll(".*\"" + name + "\":\"([^\"]+)\".*", "$1");
    }

    private String register(String token, String objectPath, String mime, long size) throws Exception {
        return mockMvc.perform(post("/api/v1/records").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SCAN\",\"objectPath\":\"" + objectPath + "\",\"originalFileName\":\"my scan\","
                                + "\"mimeType\":\"" + mime + "\",\"sizeBytes\":" + size + "}"))
                .andReturn().getResponse().getContentAsString();
    }

    private Long uploadDocument(long size) throws Exception {
        String path = field(uploadUrl(TOKEN_OWNER, "application/pdf", size), "objectPath");
        when(s3.describe(BUCKET, path)).thenReturn(Optional.of(new StoredObjectInfo(size, Instant.now())));
        String body = register(TOKEN_OWNER, path, "application/pdf", size);
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void document_singlePut_randomKey_contentTypeHeader() throws Exception {
        mockMvc.perform(post("/api/v1/records/upload-url").header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SCAN\",\"fileName\":\"f.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":1024}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SINGLE"))
                .andExpect(jsonPath("$.objectPath", startsWith("files/")))
                .andExpect(jsonPath("$.headers['Content-Type']").value("application/pdf"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void wordDocuments_refused_untilOwnerDecides() throws Exception {
        mockMvc.perform(post("/api/v1/records/upload-url").header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SCAN\",\"fileName\":\"f.docx\",\"mimeType\":"
                                + "\"application/vnd.openxmlformats-officedocument.wordprocessingml.document\",\"sizeBytes\":1024}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.mimeType").exists());
    }

    @Test
    void uploadRules_listDocumentsAndVideos_withQuota() throws Exception {
        mockMvc.perform(get("/api/v1/records/upload-rules").header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kinds", hasSize(2)))
                .andExpect(jsonPath("$.kinds[0].kind").value("DOCUMENT"))
                .andExpect(jsonPath("$.kinds[0].maxBytes").value(26214400))
                .andExpect(jsonPath("$.kinds[1].kind").value("VIDEO"))
                .andExpect(jsonPath("$.quotaBytes").value(200000000))
                .andExpect(jsonPath("$.usedBytes").value(0));
    }

    @Test
    void register_onlyByTheMotherWhoStartedTheUpload() throws Exception {
        String path = field(uploadUrl(TOKEN_OWNER, "application/pdf", 1024), "objectPath");
        when(s3.describe(BUCKET, path)).thenReturn(Optional.of(new StoredObjectInfo(1024, Instant.now())));

        mockMvc.perform(post("/api/v1/records").header("Authorization", "Bearer " + TOKEN_OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SCAN\",\"objectPath\":\"" + path + "\",\"originalFileName\":\"x\","
                                + "\"mimeType\":\"application/pdf\",\"sizeBytes\":1024}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_UPLOAD_NOT_CONFIRMED"));
    }

    @Test
    void register_sizeMismatch_deletesTheObject() throws Exception {
        String path = field(uploadUrl(TOKEN_OWNER, "application/pdf", 1024), "objectPath");
        when(s3.describe(BUCKET, path)).thenReturn(Optional.of(new StoredObjectInfo(99_000_000, Instant.now())));

        mockMvc.perform(post("/api/v1/records").header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SCAN\",\"objectPath\":\"" + path + "\",\"originalFileName\":\"x\","
                                + "\"mimeType\":\"application/pdf\",\"sizeBytes\":1024}"))
                .andExpect(status().isBadRequest());
        verify(s3).deleteObject(BUCKET, path);
        assertEquals(0, healthRecordRepository.count());
    }

    @Test
    void scanGate_pendingIsNotReady_cleanDownloads_infectedBlocked() throws Exception {
        Long id = uploadDocument(1024);

        mockMvc.perform(get("/api/v1/records/" + id).header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(jsonPath("$.scanStatus").value("PENDING"))
                .andExpect(jsonPath("$.available").value(false));
        mockMvc.perform(post("/api/v1/records/" + id + "/download-url").header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_NOT_READY"));

        when(s3.readScanStatus(eq(BUCKET), anyString())).thenReturn(ScanVerdict.CLEAN);
        mockMvc.perform(post("/api/v1/records/" + id + "/download-url").header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"disposition\":\"attachment\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("https://s3.test/get"));
        verify(s3).createSignedDownloadUrl(eq(BUCKET), anyString(), eq(Duration.ofMinutes(10)), eq("application/pdf"),
                eq("attachment; filename=\"NartureBelle-document-" + id + ".pdf\""));

        when(s3.readScanStatus(eq(BUCKET), anyString())).thenReturn(ScanVerdict.INFECTED);
        Long infected = uploadDocument(2048);
        mockMvc.perform(post("/api/v1/records/" + infected + "/download-url").header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_BLOCKED"));
    }

    @Test
    void scanningOnByDefault_notScannedS3File_fromAStageTestWithScanningOff_isNotReady() throws Exception {
        Long id = uploadDocument(1024);
        HealthRecord record = healthRecordRepository.findById(id).orElseThrow();
        assertEquals(ScanVerdict.PENDING, record.getScanStatus(), "default: every S3 upload waits for the scan");
        record.setScanStatus(ScanVerdict.NOT_SCANNED);
        healthRecordRepository.save(record);

        mockMvc.perform(get("/api/v1/records/" + id).header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(jsonPath("$.available").value(false));
        mockMvc.perform(post("/api/v1/records/" + id + "/download-url").header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_NOT_READY"));
    }

    @Test
    void largeVideo_multipart_partLinks_complete_register_withRemovalDates() throws Exception {
        long size = 100L * 1024 * 1024;
        String start = uploadUrl(TOKEN_OWNER, "video/mp4", size);
        String path = field(start, "objectPath");
        assertTrue(path.startsWith("videos/"));
        assertTrue(start.contains("\"mode\":\"MULTIPART\""));
        assertTrue(start.contains("\"partCount\":7"), start); // 100 MB in 16 MB parts

        // Someone else can't get links for her upload.
        mockMvc.perform(post("/api/v1/records/uploads/upload-1/part-urls").header("Authorization", "Bearer " + TOKEN_OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectPath\":\"" + path + "\",\"partNumbers\":[1]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/records/uploads/upload-1/part-urls").header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectPath\":\"" + path + "\",\"partNumbers\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts", hasSize(3)))
                .andExpect(jsonPath("$.parts[2].url").value("https://s3.test/part/3"));

        mockMvc.perform(post("/api/v1/records/uploads/upload-1/part-urls").header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectPath\":\"" + path + "\",\"partNumbers\":[8]}"))
                .andExpect(status().isBadRequest());

        // Register before completing is refused.
        assertTrue(register(TOKEN_OWNER, path, "video/mp4", size).contains("RECORD_UPLOAD_NOT_CONFIRMED"));

        StringBuilder parts = new StringBuilder();
        for (int i = 1; i <= 7; i++) parts.append(i > 1 ? "," : "").append("{\"partNumber\":").append(i).append(",\"etag\":\"e").append(i).append("\"}");
        mockMvc.perform(post("/api/v1/records/uploads/upload-1/complete").header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectPath\":\"" + path + "\",\"parts\":[" + parts + "]}"))
                .andExpect(status().isNoContent());
        verify(s3).completeMultipartUpload(eq(BUCKET), eq(path), eq("upload-1"), argThat(l -> l.size() == 7));

        Instant created = Instant.now();
        when(s3.describe(BUCKET, path)).thenReturn(Optional.of(new StoredObjectInfo(size, created)));
        String record = register(TOKEN_OWNER, path, "video/mp4", size);
        assertTrue(record.contains("\"kind\":\"VIDEO\""), record);
        assertTrue(record.contains("\"removalAt\":\""), record);
        assertTrue(record.contains("\"warnFrom\":\""), record);
        assertTrue(record.contains("\"trashedAt\":null"), record);
        assertEquals(0, pendingUploadRepository.count());
    }

    @Test
    void videoInTrash_isShownUnavailable_thenHiddenAfterTrash_andSupportCanRestore() throws Exception {
        String path = field(uploadUrl(TOKEN_OWNER, "video/mp4", 1024), "objectPath");
        when(s3.describe(BUCKET, path)).thenReturn(Optional.of(new StoredObjectInfo(1024, Instant.now())));
        when(s3.readScanStatus(eq(BUCKET), anyString())).thenReturn(ScanVerdict.CLEAN);
        Long id = Long.valueOf(register(TOKEN_OWNER, path, "video/mp4", 1024).replaceAll(".*\"id\":(\\d+).*", "$1"));

        // Share can't outlive the video.
        mockMvc.perform(post("/api/v1/records/" + id + "/shares").header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareTargetType\":\"PROVIDER\",\"targetProviderId\":1,\"expiresAt\":\""
                                + LocalDateTime.now().plusDays(60) + "\"}"))
                .andExpect(status().isBadRequest());

        HealthRecord video = healthRecordRepository.findById(id).orElseThrow();
        video.setRemovalAt(LocalDateTime.now().minusDays(1));
        healthRecordRepository.save(video);

        mockMvc.perform(get("/api/v1/records").header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].available").value(false))
                .andExpect(jsonPath("$[0].trashedAt").exists())
                .andExpect(jsonPath("$[0].permanentDeletionAt").exists());
        mockMvc.perform(post("/api/v1/records/" + id + "/download-url").header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_IN_TRASH"));

        // Trash used up quota space no longer.
        mockMvc.perform(get("/api/v1/records/upload-rules").header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(jsonPath("$.usedBytes").value(0));

        // Support restore: fresh 30 days, scanned again.
        when(s3.restoreLatestVersion(BUCKET, path, "video/mp4"))
                .thenReturn(Optional.of(new StoredObjectInfo(1024, Instant.now())));
        mockMvc.perform(post("/api/v1/admin/records/" + id + "/restore-video").header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"mother asked by email\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanStatus").value("PENDING"))
                .andExpect(jsonPath("$.trashedAt").doesNotExist());
        mockMvc.perform(post("/api/v1/admin/records/" + id + "/restore-video").header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());

        // After the trash period it's gone from her list.
        video = healthRecordRepository.findById(id).orElseThrow();
        video.setRemovalAt(LocalDateTime.now().minusDays(8));
        healthRecordRepository.save(video);
        mockMvc.perform(get("/api/v1/records").header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void quota_refusesUploadsPastTheLimit() throws Exception {
        uploadDocument(20_000_000);
        for (int i = 0; i < 8; i++) uploadDocument(20_000_000);
        mockMvc.perform(post("/api/v1/records/upload-url").header("Authorization", "Bearer " + TOKEN_OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SCAN\",\"fileName\":\"f.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":20000001}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORAGE_QUOTA_EXCEEDED"));
    }

    @Test
    void abort_cancelsHerUpload() throws Exception {
        String path = field(uploadUrl(TOKEN_OWNER, "video/mp4", 100L * 1024 * 1024), "objectPath");
        mockMvc.perform(delete("/api/v1/records/uploads/upload-1").param("objectPath", path)
                        .header("Authorization", "Bearer " + TOKEN_OWNER))
                .andExpect(status().isNoContent());
        verify(s3).abortMultipartUpload(BUCKET, path, "upload-1");
        assertEquals(0, pendingUploadRepository.count());
    }
}
