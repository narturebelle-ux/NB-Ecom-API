package com.narturebelle.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.mail.MailsClient;
import com.narturebelle.model.JobApplication;
import com.narturebelle.model.Role;
import com.narturebelle.model.ScanVerdict;
import com.narturebelle.model.User;
import com.narturebelle.providers.model.ApplicationDocument;
import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.providers.model.ProfileChangeRequest;
import com.narturebelle.providers.model.ProfileChangeStatus;
import com.narturebelle.providers.model.ProfileChangeType;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.providers.repository.ApplicationDocumentContentRepository;
import com.narturebelle.providers.repository.ApplicationDocumentRepository;
import com.narturebelle.providers.repository.ProfileChangeRequestRepository;
import com.narturebelle.providers.repository.ProviderApplicationRepository;
import com.narturebelle.repository.JobApplicationRepository;
import com.narturebelle.repository.UploadIntentRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import com.narturebelle.storage.DirectUploadService;
import com.narturebelle.storage.S3StorageProvider;
import com.narturebelle.storage.SignedDownloadUrl;
import com.narturebelle.storage.SignedUploadUrl;
import com.narturebelle.storage.StorageBackend;
import com.narturebelle.storage.StoredObjectInfo;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider documents, provider change-request documents and careers résumés uploaded straight to S3
 * (owner, 12 Nov 2026), with S3 mocked: upload rules, upload links, ownership checks, saving, scan gating,
 * download links, and the multipart fallback still working.
 */
@SpringBootTest(properties = {"storage.provider=s3", "storage.s3.bucket=nb-files-test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class S3ProviderCareersUploadsTest {

    private static final String BUCKET = "nb-files-test";
    private static final String OPERATIONS = "s3up-operations";
    private static final String ADMIN = "s3up-admin";
    private static final long MB = 1024L * 1024;
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderApplicationRepository applicationRepository;
    @Autowired private ApplicationDocumentRepository documentRepository;
    @Autowired private ApplicationDocumentContentRepository contentRepository;
    @Autowired private ProfileChangeRequestRepository changeRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private UploadIntentRepository intentRepository;
    @Autowired private DirectUploadService directUploadService;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private MailsClient mailsClient;
    @MockBean private S3StorageProvider s3;

    private String applicant;
    private String otherApplicant;
    private String ip;

    @BeforeEach
    void setUp() throws Exception {
        intentRepository.deleteAll();
        createUser(OPERATIONS, Role.OPERATIONS_ADMIN);
        createUser(ADMIN, Role.SUPER_ADMIN);
        int n = NEXT.getAndIncrement();
        applicant = "s3up-applicant-" + n;
        otherApplicant = "s3up-other-" + n;
        createUser(applicant, Role.MOTHER);
        createUser(otherApplicant, Role.MOTHER);
        ip = "198.51.100." + n;

        when(mailsClient.send(any())).thenReturn(true);
        when(s3.bucket()).thenReturn(BUCKET);
        when(s3.createSignedUploadUrl(anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(new SignedUploadUrl("https://s3.test/put", null, LocalDateTime.now().plusMinutes(15)));
        when(s3.createSignedDownloadUrl(anyString(), anyString(), any(Duration.class), any(), any()))
                .thenReturn(new SignedDownloadUrl("https://s3.test/get", LocalDateTime.now().plusMinutes(10)));
        when(s3.readScanStatus(anyString(), anyString())).thenReturn(ScanVerdict.PENDING);
    }

    private void createUser(String token, Role role) throws Exception {
        User user = userRepository.findByFirebaseUid("uid-" + token).orElseGet(User::new);
        user.setFirebaseUid("uid-" + token);
        user.setEmail(token + "@example.com");
        user.setEmailVerified(true);
        user.setRoles(new HashSet<>(Set.of(role)));
        userRepository.save(user);
        when(firebaseTokenVerifier.verify(token)).thenReturn(
                new VerifiedFirebaseToken("uid-" + token, token + "@example.com", true, "+919900000000", null));
    }

    // ---- provider application ----

    @Test
    void rulesForAQuestionAreDocumentsOnly() throws Exception {
        start(applicant);
        call(get("/api/v1/provider-application/files/upload-rules").param("questionKey", "panCard"), applicant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kinds.length()").value(1))
                .andExpect(jsonPath("$.kinds[0].kind").value("DOCUMENT"))
                .andExpect(jsonPath("$.kinds[0].maxBytes").value(20 * MB))
                .andExpect(jsonPath("$.quotaBytes").doesNotExist());
        call(get("/api/v1/provider-application/files/upload-rules").param("questionKey", "legalName"), applicant)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    }

    @Test
    void anApplicantUploadsAndSavesADocument_thenItOpensOnceScannedClean() throws Exception {
        start(applicant);
        JsonNode link = uploadUrl(applicant, "panCard", "pan.pdf", "application/pdf", 10 * MB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SINGLE"))
                .andExpect(jsonPath("$.headers['Content-Type']").value("application/pdf"))
                .andExpect(jsonPath("$.uploadToken").doesNotExist())
                .andReturn().getResponse().getContentAsString().transform(this::read);
        String path = link.get("objectPath").asText();
        assertTrue(path.startsWith("files/"), path);
        arrived(path, 10 * MB);

        JsonNode doc = save(applicant, "panCard", path, "application/pdf", 10 * MB)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("DOCUMENT"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.scanStatus").value("PENDING"))
                .andExpect(jsonPath("$.available").value(false))
                .andReturn().getResponse().getContentAsString().transform(this::read);
        long id = doc.get("id").asLong();
        ApplicationDocument stored = documentRepository.findById(id).orElseThrow();
        assertEquals(StorageBackend.S3, stored.getStorageProvider());
        assertEquals(path, stored.getStorageObjectPath());
        assertFalse(contentRepository.existsById(id));
        assertEquals(0, intentRepository.count());

        call(post("/api/v1/provider-application/documents/" + id + "/download-url"), applicant)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FILE_NOT_READY"));

        when(s3.readScanStatus(BUCKET, path)).thenReturn(ScanVerdict.CLEAN);
        call(post("/api/v1/provider-application/documents/" + id + "/download-url"), applicant)
                .andExpect(status().isOk()).andExpect(jsonPath("$.downloadUrl").value("https://s3.test/get"));
        call(post("/api/v1/admin/provider-applications/documents/" + id + "/download-url")
                .contentType(MediaType.APPLICATION_JSON).content("{\"disposition\":\"attachment\"}"), OPERATIONS)
                .andExpect(status().isOk());
        verify(s3).createSignedDownloadUrl(eq(BUCKET), eq(path), any(Duration.class), eq("application/pdf"),
                eq("attachment; filename=\"NartureBelle-document-" + id + ".pdf\""));
        // S3 files are too big for Vercel to stream, so /file points the UI at download-url.
        call(get("/api/v1/admin/provider-applications/documents/" + id + "/file"), OPERATIONS)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USE_DOWNLOAD_URL"));
        call(get("/api/v1/provider-application"), applicant)
                .andExpect(status().isOk()).andExpect(jsonPath("$.documents[0].available").value(true));
    }

    @Test
    void anUploadCanOnlyBeSavedByItsApplicant_forItsOwnQuestion() throws Exception {
        start(applicant);
        start(otherApplicant);
        String path = objectPath(uploadUrl(applicant, "panCard", "pan.pdf", "application/pdf", MB));
        arrived(path, MB);

        save(otherApplicant, "panCard", path, "application/pdf", MB)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_NOT_CONFIRMED"));
        save(applicant, "photoId", path, "application/pdf", MB)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_NOT_CONFIRMED"));
        save(applicant, "panCard", path, "application/pdf", MB).andExpect(status().isCreated());
        // Once saved it can't be claimed again.
        save(applicant, "panCard", path, "application/pdf", MB)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_NOT_CONFIRMED"));
    }

    @Test
    void wrongTypesAndSizesAreRefusedBeforeUploading() throws Exception {
        start(applicant);
        uploadUrl(applicant, "panCard", "clip.mp4", "video/mp4", MB)
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_ALLOWED"));
        uploadUrl(applicant, "panCard", "pan.exe", "application/pdf", MB)
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_ALLOWED"));
        uploadUrl(applicant, "panCard", "pan.pdf", "application/pdf", 21 * MB)
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("DOCUMENT_TOO_LARGE"));
    }

    @Test
    void aFileOfTheWrongSizeIsRemoved() throws Exception {
        start(applicant);
        String path = objectPath(uploadUrl(applicant, "panCard", "pan.pdf", "application/pdf", MB));
        arrived(path, 5 * MB);
        save(applicant, "panCard", path, "application/pdf", MB)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_SIZE_MISMATCH"));
        verify(s3).deleteObject(BUCKET, path);
    }

    @Test
    void replacingADocumentRemovesTheOldFile_andTheMultipartUploadStillWorks() throws Exception {
        start(applicant);
        String first = objectPath(uploadUrl(applicant, "panCard", "pan.pdf", "application/pdf", MB));
        arrived(first, MB);
        save(applicant, "panCard", first, "application/pdf", MB).andExpect(status().isCreated());

        String second = objectPath(uploadUrl(applicant, "panCard", "pan2.png", "image/png", MB));
        arrived(second, MB);
        save(applicant, "panCard", second, "image/png", MB).andExpect(status().isCreated());
        verify(s3).deleteObject(BUCKET, first);

        // The older upload through the API still works, and replaces the S3 file.
        byte[] pdf = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);
        call(multipart("/api/v1/provider-application/documents/panCard")
                .file(new MockMultipartFile("file", "pan.pdf", "application/pdf", pdf)), applicant)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.scanStatus").doesNotExist());
        verify(s3).deleteObject(BUCKET, second);
        ApplicationDocument doc = documentRepository.findAll().stream()
                .filter(d -> d.getQuestionKey().equals("panCard") && d.getStorageProvider() == null).findFirst().orElseThrow();
        assertTrue(contentRepository.existsById(doc.getId()));
        call(post("/api/v1/provider-application/documents/" + doc.getId() + "/download-url"), applicant)
                .andExpect(status().isNotFound());
    }

    @Test
    void aLockedApplicationTakesNoUploadLinks() throws Exception {
        start(applicant);
        ProviderApplication app = currentApp(applicant);
        app.setStatus(ApplicationStatus.OPERATIONS_REVIEW);
        applicationRepository.save(app);
        uploadUrl(applicant, "panCard", "pan.pdf", "application/pdf", MB)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_LOCKED"));
    }

    // ---- provider change requests ----

    @Test
    void aLiveProfessionalAttachesAChangeDocumentFromS3() throws Exception {
        start(applicant);
        ProviderApplication app = currentApp(applicant);
        app.setStatus(ApplicationStatus.ACTIVE);
        applicationRepository.save(app);
        ProfileChangeRequest change = new ProfileChangeRequest();
        change.setApplicationId(app.getId());
        change.setChangeType(ProfileChangeType.LICENCE);
        change.setPayloadEncrypted("test");
        change.setStatus(ProfileChangeStatus.PENDING);
        long changeId = changeRepository.save(change).getId();

        call(get("/api/v1/provider-workspace/files/upload-rules").param("changeRequestId", String.valueOf(changeId)), applicant)
                .andExpect(status().isOk()).andExpect(jsonPath("$.kinds[0].kind").value("DOCUMENT"));
        String path = objectPath(call(post("/api/v1/provider-workspace/files/upload-url").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fileName", "renewed.pdf", "mimeType", "application/pdf", "sizeBytes", MB,
                        "changeRequestId", changeId))), applicant).andExpect(status().isOk()));
        arrived(path, MB);

        Map<String, Object> body = savedFile(path, "application/pdf", MB);
        call(post("/api/v1/provider-workspace/change-requests/" + changeId + "/document")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)), applicant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentAttached").value(true));
        ApplicationDocument pending = documentRepository.findByApplicationIdAndQuestionKey(app.getId(), "pending-registrationCertificate")
                .orElseThrow();
        assertEquals(path, pending.getStorageObjectPath());

        // A change-request upload link must say which change it is for.
        call(post("/api/v1/provider-workspace/files/upload-url").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fileName", "x.pdf", "mimeType", "application/pdf", "sizeBytes", MB))), applicant)
                .andExpect(status().isBadRequest());
    }

    // ---- careers ----

    @Test
    void anApplicantUploadsAResumeToS3_andAppliesWithItsToken() throws Exception {
        long jobId = postJob();
        mockMvc.perform(get("/api/v1/careers/jobs/" + jobId + "/files/upload-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kinds.length()").value(1))
                .andExpect(jsonPath("$.kinds[0].extensions.length()").value(3))
                .andExpect(jsonPath("$.kinds[0].maxBytes").value(10 * MB));

        JsonNode link = read(resumeUploadUrl(jobId, "cv.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 6 * MB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SINGLE"))
                .andReturn().getResponse().getContentAsString());
        String path = link.get("objectPath").asText();
        String token = link.get("uploadToken").asText();
        assertTrue(path.startsWith("files/"));
        assertTrue(token.length() >= 40);
        arrived(path, 6 * MB);

        // Without the right token the file can't be claimed.
        long before = jobApplicationRepository.count();
        applyJson(jobId, path, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 6 * MB, "wrong-token")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_NOT_CONFIRMED"));
        assertEquals(before, jobApplicationRepository.count());

        String body = applyJson(jobId, path, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 6 * MB, token)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long applicationId = read(body).get("id").asLong();
        JobApplication saved = jobApplicationRepository.findById(applicationId).orElseThrow();
        assertEquals(StorageBackend.S3, saved.getResumeStorageProvider());
        assertEquals(path, saved.getResumeObjectPath());
        assertEquals("cv.docx", saved.getResumeFileName());
        assertEquals(6 * MB, saved.getResumeSizeBytes());

        mockMvc.perform(post("/api/v1/admin/careers/applications/" + applicationId + "/resume/download-url")
                        .header("Authorization", "Bearer " + ADMIN))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FILE_NOT_READY"));
        when(s3.readScanStatus(BUCKET, path)).thenReturn(ScanVerdict.CLEAN);
        mockMvc.perform(post("/api/v1/admin/careers/applications/" + applicationId + "/resume/download-url")
                        .header("Authorization", "Bearer " + ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.downloadUrl").value("https://s3.test/get"));
        mockMvc.perform(get("/api/v1/admin/careers/applications/" + applicationId + "/resume")
                        .header("Authorization", "Bearer " + ADMIN))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USE_DOWNLOAD_URL"));
        // Résumés are documents only.
        resumeUploadUrl(jobId, "cv.mp4", "video/mp4", MB)
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("RESUME_TYPE_NOT_ALLOWED"));
    }

    @Test
    void aResumeUploadIsTiedToItsJob() throws Exception {
        long jobId = postJob();
        long otherJob = postJob();
        JsonNode link = read(resumeUploadUrl(jobId, "cv.pdf", "application/pdf", MB)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        arrived(link.get("objectPath").asText(), MB);
        applyJson(otherJob, link.get("objectPath").asText(), "application/pdf", MB, link.get("uploadToken").asText())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_NOT_CONFIRMED"));
    }

    @Test
    void unsavedUploadsAreSweptWithTheirFiles() throws Exception {
        long jobId = postJob();
        String path = read(resumeUploadUrl(jobId, "cv.pdf", "application/pdf", MB)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("objectPath").asText();
        assertEquals(0, directUploadService.sweepStale(LocalDateTime.now()));
        assertEquals(1, directUploadService.sweepStale(LocalDateTime.now().plusDays(2)));
        verify(s3).deleteObject(BUCKET, path);
        assertEquals(0, intentRepository.count());
    }

    // ---- helpers ----

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private void start(String token) throws Exception {
        call(post("/api/v1/provider-application").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("profession", "GYNECOLOGIST", "termsAccepted", true, "verificationConsent", true))), token)
                .andExpect(status().isCreated());
    }

    private ProviderApplication currentApp(String token) {
        Long userId = userRepository.findByFirebaseUid("uid-" + token).orElseThrow().getId();
        return applicationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).orElseThrow();
    }

    private ResultActions uploadUrl(String token, String questionKey, String fileName, String mime, long size) throws Exception {
        return call(post("/api/v1/provider-application/files/upload-url").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fileName", fileName, "mimeType", mime, "sizeBytes", size, "questionKey", questionKey))), token);
    }

    private ResultActions save(String token, String questionKey, String path, String mime, long size) throws Exception {
        return call(post("/api/v1/provider-application/documents/" + questionKey).contentType(MediaType.APPLICATION_JSON)
                .content(json(savedFile(path, mime, size))), token);
    }

    private static Map<String, Object> savedFile(String path, String mime, long size) {
        return Map.of("objectPath", path, "originalFileName", "my file", "mimeType", mime, "sizeBytes", size);
    }

    private void arrived(String path, long size) {
        when(s3.describe(BUCKET, path)).thenReturn(Optional.of(new StoredObjectInfo(size, Instant.now())));
    }

    private String objectPath(ResultActions result) throws Exception {
        JsonNode node = read(result.andReturn().getResponse().getContentAsString());
        return node.has("objectPath") ? node.get("objectPath").asText() : null;
    }

    private long postJob() throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/careers/jobs").header("Authorization", "Bearer " + ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nurse lead\",\"team\":\"Care\",\"location\":\"Bengaluru\","
                                + "\"employmentType\":\"FULL_TIME\",\"workMode\":\"HYBRID\",\"summary\":\"Lead our nurses\","
                                + "\"description\":\"Line one\",\"requirements\":[\"GNM\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return read(body).get("id").asLong();
    }

    private ResultActions resumeUploadUrl(long jobId, String fileName, String mime, long size) throws Exception {
        return mockMvc.perform(post("/api/v1/careers/jobs/" + jobId + "/files/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fileName", fileName, "mimeType", mime, "sizeBytes", size)))
                .with(request -> { request.setRemoteAddr(ip); return request; }));
    }

    private ResultActions applyJson(long jobId, String path, String mime, long size, String token) throws Exception {
        Map<String, Object> resume = new HashMap<>(savedFile(path, mime, size));
        resume.put("originalFileName", "cv.docx");
        resume.put("uploadToken", token);
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Asha Rao");
        body.put("email", "asha" + NEXT.getAndIncrement() + "@example.com");
        body.put("phone", "+91 98450 12345");
        body.put("yearsExperience", 6);
        body.put("privacyConsent", true);
        body.put("resume", resume);
        return mockMvc.perform(post("/api/v1/careers/jobs/" + jobId + "/applications")
                .contentType(MediaType.APPLICATION_JSON).content(json(body))
                .with(request -> { request.setRemoteAddr(ip); return request; }));
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

}
