package com.narturebelle.providers.service;

import com.narturebelle.providers.dto.FormDtos.FormQuestion;
import com.narturebelle.providers.dto.FormDtos.QuestionType;
import com.narturebelle.providers.exception.ProviderOnboardingException;
import com.narturebelle.providers.model.ApplicationDocument;
import com.narturebelle.providers.model.ApplicationDocumentContent;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.providers.repository.ApplicationDocumentContentRepository;
import com.narturebelle.providers.repository.ApplicationDocumentRepository;
import com.narturebelle.providers.repository.RequestedChangeRepository;
import com.narturebelle.dto.DirectUploadRequests;
import com.narturebelle.model.FileKind;
import com.narturebelle.model.ScanVerdict;
import com.narturebelle.model.UploadPurpose;
import com.narturebelle.providers.dto.ApplicationDtos.DocumentResponse;
import com.narturebelle.security.AuditService;
import com.narturebelle.storage.DirectUploadService;
import com.narturebelle.storage.DirectUploadService.FileRule;
import com.narturebelle.storage.DirectUploadService.StoredFile;
import com.narturebelle.storage.DirectUploadService.UploadPolicy;
import com.narturebelle.storage.DirectUploadService.UploadRules;
import com.narturebelle.storage.DirectUploadService.UploadScope;
import com.narturebelle.storage.DirectUploadService.UploadUrl;
import com.narturebelle.storage.SignedDownloadUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Certificates and IDs. New files go straight from the browser to S3 (owner, 12 Nov 2026) through
 * DirectUploadService, under files/ with random names and the GuardDuty scan. The older multipart upload
 * into Postgres still works during the move (and checks the bytes, not just the name); files stored that
 * way stay readable. Which questions also take a video is config (none by default).
 */
@Service
public class ApplicationDocumentService {

    /** 4 MB: Vercel rejects any request body over 4.5 MB. */
    static final long MAX_DOCUMENT_BYTES = 4L * 1024 * 1024;
    public static final String PENDING_PREFIX = "pending-";

    private static final Map<String, DocumentType> TYPES = Map.of(
            "pdf", new DocumentType("application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'}),
            "jpg", new DocumentType("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
            "jpeg", new DocumentType("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
            "png", new DocumentType("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}));

    private record DocumentType(String contentType, byte[] magic) {}

    public record DownloadedDocument(ApplicationDocument document, byte[] content) {}

    /** What S3 uploads accept: PDF, JPG and PNG, like the multipart upload, but up to 20 MB by default. */
    private static final List<String> DOCUMENT_MIME_TYPES = List.of("application/pdf", "image/jpeg", "image/png");
    private static final List<String> DOCUMENT_EXTENSIONS = List.of("pdf", "jpg", "jpeg", "png");

    private final ApplicationDocumentRepository documentRepository;
    private final ApplicationDocumentContentRepository contentRepository;
    private final RequestedChangeRepository requestedChangeRepository;
    private final ApplicationFormCatalog catalog;
    private final ApplicationDocumentScanner documentScanner;
    private final AuditService auditService;
    private final DirectUploadService directUploads;
    private final FileRule documentRule;
    private final FileRule videoRule;
    private final Set<String> videoQuestionKeys;

    public ApplicationDocumentService(ApplicationDocumentRepository documentRepository,
                                      ApplicationDocumentContentRepository contentRepository,
                                      RequestedChangeRepository requestedChangeRepository, ApplicationFormCatalog catalog,
                                      ApplicationDocumentScanner documentScanner, AuditService auditService,
                                      DirectUploadService directUploads,
                                      @Value("${provider-onboarding.upload.max-size-bytes:20971520}") long documentMaxBytes,
                                      @Value("${provider-onboarding.upload.video-question-keys:}") String videoQuestionKeys,
                                      @Value("${provider-onboarding.upload.video-max-size-bytes:524288000}") long videoMaxBytes) {
        this.documentRepository = documentRepository;
        this.contentRepository = contentRepository;
        this.requestedChangeRepository = requestedChangeRepository;
        this.catalog = catalog;
        this.documentScanner = documentScanner;
        this.auditService = auditService;
        this.directUploads = directUploads;
        this.documentRule = new FileRule(FileKind.DOCUMENT, DOCUMENT_MIME_TYPES, DOCUMENT_EXTENSIONS, documentMaxBytes);
        this.videoRule = new FileRule(FileKind.VIDEO, List.of("video/mp4", "video/quicktime", "video/webm"),
                List.of("mp4", "mov", "webm"), videoMaxBytes);
        this.videoQuestionKeys = Arrays.stream(videoQuestionKeys.split(",")).map(String::trim)
                .filter(k -> !k.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    // ---- Direct-to-S3 uploads ----

    /** Documents for every file question; a video too only for questions listed in PROVIDER_VIDEO_QUESTION_KEYS. */
    private UploadPolicy policyFor(String questionKey) {
        List<FileRule> rules = new ArrayList<>();
        rules.add(documentRule);
        boolean video = questionKey != null && videoQuestionKeys.contains(questionKey);
        if (video) rules.add(videoRule);
        return new UploadPolicy(rules, "DOCUMENT_TYPE_NOT_ALLOWED", "DOCUMENT_TOO_LARGE", video, false);
    }

    private static UploadScope applicationScope(ProviderApplication application, String questionKey) {
        return new UploadScope(UploadPurpose.PROVIDER_APPLICATION, application.getUserId(), application.getId(), questionKey);
    }

    public static UploadScope changeRequestScope(ProviderApplication application, Long changeRequestId) {
        return new UploadScope(UploadPurpose.PROVIDER_CHANGE_REQUEST, application.getUserId(), changeRequestId, null);
    }

    /** The rules for one file question, so the UI can check a file before uploading it. */
    public UploadRules uploadRules(ProviderApplication application, String questionKey) {
        requireFileQuestion(application, questionKey);
        return directUploads.rules(policyFor(questionKey));
    }

    public UploadUrl issueUploadUrl(ProviderApplication application, DirectUploadRequests.UploadUrl req) {
        requireUploadable(application, req.questionKey());
        return directUploads.issue(policyFor(req.questionKey()), applicationScope(application, req.questionKey()),
                req.fileName(), req.mimeType(), req.sizeBytes());
    }

    /** For part-urls, complete and abort, which match an upload on purpose and owner only. */
    public static UploadScope ownerScope(UploadPurpose purpose, Long userId) {
        return new UploadScope(purpose, userId, null, null);
    }

    /** The save step after an S3 upload: same checks as the multipart upload, then the document points at S3. */
    @Transactional
    public ApplicationDocument saveUploaded(ProviderApplication application, String questionKey, DirectUploadRequests.SavedFile file) {
        requireUploadable(application, questionKey);
        StoredFile stored = directUploads.claim(policyFor(questionKey), applicationScope(application, questionKey),
                file.objectPath(), file.mimeType(), file.uploadToken());
        ApplicationDocument saved = storeS3(application.getId(), questionKey, file.originalFileName(), stored);
        auditService.record("APPLICATION_DOCUMENT_UPLOADED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), null,
                Map.of("question", questionKey, "storageProvider", "S3", "kind", stored.kind().name()));
        return saved;
    }

    /** Change requests' upload rules: documents only. */
    public UploadPolicy changeRequestPolicy() {
        return policyFor(null);
    }

    /** A change request's file after an S3 upload, kept under a pending key until the change is approved. */
    @Transactional
    public ApplicationDocument storePendingUploaded(ProviderApplication application, Long changeRequestId, String questionKey,
                                                    DirectUploadRequests.SavedFile file) {
        StoredFile stored = directUploads.claim(changeRequestPolicy(), changeRequestScope(application, changeRequestId),
                file.objectPath(), file.mimeType(), file.uploadToken());
        return storeS3(application.getId(), PENDING_PREFIX + questionKey, file.originalFileName(), stored);
    }

    private ApplicationDocument storeS3(Long applicationId, String key, String originalFileName, StoredFile stored) {
        ApplicationDocument document = documentRepository.findByApplicationIdAndQuestionKey(applicationId, key).orElseGet(ApplicationDocument::new);
        dropStoredBytes(document);
        document.setApplicationId(applicationId);
        document.setQuestionKey(key);
        document.setFileName(safeFileName(originalFileName, directUploads.extensionFor(stored.mimeType())));
        document.setContentType(stored.mimeType());
        document.setSizeBytes((int) stored.sizeBytes());
        document.setUploadedAt(LocalDateTime.now());
        document.setStorageProvider(stored.backend());
        document.setStorageBucket(stored.bucket());
        document.setStorageObjectPath(stored.objectPath());
        document.setScanStatus(stored.scanStatus());
        document.setKind(stored.kind());
        return documentRepository.save(document);
    }

    /** The API shape, with the latest scan verdict and whether the file can be opened now. */
    public DocumentResponse toResponse(ApplicationDocument document) {
        refreshScan(document);
        boolean available = !document.isOnS3() || directUploads.allowsDownload(document.getStorageProvider(), document.getScanStatus());
        return DocumentResponse.from(document, available);
    }

    private void refreshScan(ApplicationDocument document) {
        if (!document.isOnS3() || document.getScanStatus() != ScanVerdict.PENDING) return;
        ScanVerdict verdict = directUploads.refreshScan(document.getStorageProvider(), document.getStorageBucket(),
                document.getStorageObjectPath(), document.getScanStatus());
        if (verdict != document.getScanStatus()) {
            document.setScanStatus(verdict);
            documentRepository.save(document);
            auditService.record(verdict == ScanVerdict.CLEAN ? "APPLICATION_DOCUMENT_SCAN_CLEAN" : "APPLICATION_DOCUMENT_SCAN_BLOCKED",
                    "PROVIDER_APPLICATION", String.valueOf(document.getApplicationId()), null, Map.of("scanStatus", verdict.name()));
        }
    }

    /**
     * A 10-minute link to an S3 document. Older Postgres files answer 404 so the UI falls back to the /file
     * endpoint. applicationId null = staff, who may open any application's file.
     */
    public DownloadLink downloadUrl(Long applicationId, Long documentId, String disposition) {
        ApplicationDocument document = documentRepository.findById(documentId)
                .filter(found -> applicationId == null || found.getApplicationId().equals(applicationId))
                .orElseThrow(() -> new ProviderOnboardingException(404, "DOCUMENT_NOT_FOUND", "Document not found."));
        if (!document.isOnS3()) {
            throw new ProviderOnboardingException(404, "DOCUMENT_NOT_ON_S3", "Download this document with its /file link.");
        }
        refreshScan(document);
        SignedDownloadUrl signed = directUploads.downloadUrl(document.getStorageBucket(), document.getStorageObjectPath(),
                document.getScanStatus(), document.getContentType(), disposition,
                "NartureBelle-document-" + document.getId() + "." + directUploads.extensionFor(document.getContentType()));
        return new DownloadLink(document, signed);
    }

    public record DownloadLink(ApplicationDocument document, SignedDownloadUrl link) {}

    private void requireFileQuestion(ProviderApplication application, String questionKey) {
        FormQuestion question = questionKey == null ? null : catalog.questionsByKey(application.getProfessionalType()).get(questionKey);
        if (question == null || question.type() != QuestionType.FILE) {
            throw new ProviderOnboardingException(404, "QUESTION_NOT_FOUND", "That question doesn't take a file.");
        }
    }

    private void requireUploadable(ProviderApplication application, String questionKey) {
        if (!application.getStatus().isEditable()) {
            throw new ProviderOnboardingException(409, "APPLICATION_LOCKED", "This application can no longer be changed.");
        }
        requireFileQuestion(application, questionKey);
        requireUnlocked(application, questionKey);
    }

    /** Before a document gets new bytes: removes the old S3 object or Postgres content it pointed at. */
    private void dropStoredBytes(ApplicationDocument document) {
        if (document.getId() == null) return;
        if (document.isOnS3()) {
            directUploads.deleteQuietly(document.getStorageBucket(), document.getStorageObjectPath());
        } else if (contentRepository.existsById(document.getId())) {
            contentRepository.deleteById(document.getId());
            contentRepository.flush();
        }
    }

    /** An applicant's upload for one file question, replacing any earlier file for it. */
    @Transactional
    public ApplicationDocument upload(ProviderApplication application, String questionKey, MultipartFile file) {
        requireUploadable(application, questionKey);
        ApplicationDocument saved = store(application.getId(), questionKey, file);
        auditService.record("APPLICATION_DOCUMENT_UPLOADED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), null,
                Map.of("question", questionKey));
        return saved;
    }

    /** An applicant removing their own file. Locked files stay, so resubmitting can't get stuck on one they can't re-upload. */
    @Transactional
    public void removeByApplicant(ProviderApplication application, String questionKey) {
        requireUnlocked(application, questionKey);
        remove(application.getId(), questionKey);
    }

    private void requireUnlocked(ProviderApplication application, String questionKey) {
        var requested = requestedChangeRepository.findByApplicationId(application.getId());
        if (!requested.isEmpty() && requested.stream().noneMatch(change -> change.getQuestionKey().equals(questionKey))) {
            throw new ProviderOnboardingException(409, "FIELD_LOCKED", "Only the fields our reviewers asked about can be changed now.");
        }
    }

    /** Stores a file for a live professional's change request, under a pending key until the change is approved. */
    @Transactional
    public ApplicationDocument storePending(Long applicationId, String questionKey, MultipartFile file) {
        return store(applicationId, PENDING_PREFIX + questionKey, file);
    }

    /** Makes a pending file the current one once its change is approved. */
    @Transactional
    public void promotePending(Long applicationId, String questionKey) {
        documentRepository.findByApplicationIdAndQuestionKey(applicationId, PENDING_PREFIX + questionKey).ifPresent(pending -> {
            remove(applicationId, questionKey);
            documentRepository.flush();
            pending.setQuestionKey(questionKey);
            documentRepository.save(pending);
        });
    }

    private ApplicationDocument store(Long applicationId, String key, MultipartFile file) {
        String extension = extension(file);
        byte[] content = bytes(file, extension);
        documentScanner.scan(content);

        ApplicationDocument document = documentRepository.findByApplicationIdAndQuestionKey(applicationId, key).orElseGet(ApplicationDocument::new);
        dropStoredBytes(document);
        document.setApplicationId(applicationId);
        document.setQuestionKey(key);
        document.setFileName(safeFileName(file.getOriginalFilename(), extension));
        document.setContentType(TYPES.get(extension).contentType());
        document.setSizeBytes(content.length);
        document.setUploadedAt(LocalDateTime.now());
        document.setStorageProvider(null);
        document.setStorageBucket(null);
        document.setStorageObjectPath(null);
        document.setScanStatus(null);
        document.setKind(FileKind.DOCUMENT);
        ApplicationDocument saved = documentRepository.save(document);
        contentRepository.save(new ApplicationDocumentContent(saved.getId(), content));
        return saved;
    }

    @Transactional
    public void remove(Long applicationId, String questionKey) {
        documentRepository.findByApplicationIdAndQuestionKey(applicationId, questionKey).ifPresent(this::delete);
    }

    @Transactional
    public void removeAll(Long applicationId) {
        documentRepository.findByApplicationId(applicationId).forEach(this::delete);
    }

    private void delete(ApplicationDocument document) {
        if (document.isOnS3()) {
            directUploads.deleteQuietly(document.getStorageBucket(), document.getStorageObjectPath());
        } else if (contentRepository.existsById(document.getId())) {
            contentRepository.deleteById(document.getId());
        }
        documentRepository.delete(document);
    }

    public List<ApplicationDocument> list(Long applicationId) {
        return documentRepository.findByApplicationId(applicationId);
    }

    /** The bytes of a document that belongs to this application; 404 otherwise, so ids can't be probed. */
    public DownloadedDocument download(Long applicationId, Long documentId) {
        ApplicationDocument document = documentRepository.findById(documentId)
                .filter(found -> applicationId == null || found.getApplicationId().equals(applicationId))
                .orElseThrow(() -> new ProviderOnboardingException(404, "DOCUMENT_NOT_FOUND", "Document not found."));
        if (document.isOnS3()) {
            // S3 files can be larger than Vercel lets through, so they are opened with a download-url link instead.
            throw new ProviderOnboardingException(409, "USE_DOWNLOAD_URL", "Open this document with its download-url link.");
        }
        byte[] content = contentRepository.findById(documentId)
                .orElseThrow(() -> new ProviderOnboardingException(404, "DOCUMENT_NOT_FOUND", "Document not found."))
                .getContent();
        return new DownloadedDocument(document, content);
    }

    private static String extension(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!TYPES.containsKey(extension)) {
            throw new ProviderOnboardingException(415, "DOCUMENT_TYPE_NOT_ALLOWED", "Please upload a PDF, JPG or PNG file.");
        }
        return extension;
    }

    private static byte[] bytes(MultipartFile file, String extension) {
        if (file.isEmpty()) {
            throw new ProviderOnboardingException(400, "DOCUMENT_EMPTY", "That file is empty.");
        }
        if (file.getSize() > MAX_DOCUMENT_BYTES) {
            throw new ProviderOnboardingException(413, "DOCUMENT_TOO_LARGE", "Please upload a file of 4 MB or less.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ProviderOnboardingException(400, "DOCUMENT_UNREADABLE", "We couldn't read that file. Please try again.");
        }
        byte[] magic = TYPES.get(extension).magic();
        boolean matches = content.length >= magic.length;
        for (int i = 0; matches && i < magic.length; i++) {
            matches = content[i] == magic[i];
        }
        if (!matches) {
            throw new ProviderOnboardingException(415, "DOCUMENT_TYPE_NOT_ALLOWED", "That file doesn't look like a real PDF, JPG or PNG.");
        }
        return content;
    }

    private static String safeFileName(String original, String extension) {
        String name = original == null ? "document." + extension : original.replaceAll("[^A-Za-z0-9._ -]", "_");
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
