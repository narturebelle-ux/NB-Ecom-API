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
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Certificates and IDs, kept in Postgres. The bytes are checked, not just the name, same as careers résumés. */
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

    private final ApplicationDocumentRepository documentRepository;
    private final ApplicationDocumentContentRepository contentRepository;
    private final RequestedChangeRepository requestedChangeRepository;
    private final ApplicationFormCatalog catalog;
    private final ApplicationDocumentScanner documentScanner;
    private final AuditService auditService;

    public ApplicationDocumentService(ApplicationDocumentRepository documentRepository,
                                      ApplicationDocumentContentRepository contentRepository,
                                      RequestedChangeRepository requestedChangeRepository, ApplicationFormCatalog catalog,
                                      ApplicationDocumentScanner documentScanner, AuditService auditService) {
        this.documentRepository = documentRepository;
        this.contentRepository = contentRepository;
        this.requestedChangeRepository = requestedChangeRepository;
        this.catalog = catalog;
        this.documentScanner = documentScanner;
        this.auditService = auditService;
    }

    /** An applicant's upload for one file question, replacing any earlier file for it. */
    @Transactional
    public ApplicationDocument upload(ProviderApplication application, String questionKey, MultipartFile file) {
        if (!application.getStatus().isEditable()) {
            throw new ProviderOnboardingException(409, "APPLICATION_LOCKED", "This application can no longer be changed.");
        }
        FormQuestion question = catalog.questionsByKey(application.getProfessionalType()).get(questionKey);
        if (question == null || question.type() != QuestionType.FILE) {
            throw new ProviderOnboardingException(404, "QUESTION_NOT_FOUND", "That question doesn't take a file.");
        }
        requireUnlocked(application, questionKey);
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
        document.setApplicationId(applicationId);
        document.setQuestionKey(key);
        document.setFileName(safeFileName(file.getOriginalFilename(), extension));
        document.setContentType(TYPES.get(extension).contentType());
        document.setSizeBytes(content.length);
        document.setUploadedAt(LocalDateTime.now());
        ApplicationDocument saved = documentRepository.save(document);
        contentRepository.save(new ApplicationDocumentContent(saved.getId(), content));
        return saved;
    }

    @Transactional
    public void remove(Long applicationId, String questionKey) {
        documentRepository.findByApplicationIdAndQuestionKey(applicationId, questionKey).ifPresent(document -> {
            contentRepository.deleteById(document.getId());
            documentRepository.delete(document);
        });
    }

    @Transactional
    public void removeAll(Long applicationId) {
        for (ApplicationDocument document : documentRepository.findByApplicationId(applicationId)) {
            contentRepository.deleteById(document.getId());
            documentRepository.delete(document);
        }
    }

    public List<ApplicationDocument> list(Long applicationId) {
        return documentRepository.findByApplicationId(applicationId);
    }

    /** The bytes of a document that belongs to this application; 404 otherwise, so ids can't be probed. */
    public DownloadedDocument download(Long applicationId, Long documentId) {
        ApplicationDocument document = documentRepository.findById(documentId)
                .filter(found -> applicationId == null || found.getApplicationId().equals(applicationId))
                .orElseThrow(() -> new ProviderOnboardingException(404, "DOCUMENT_NOT_FOUND", "Document not found."));
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
