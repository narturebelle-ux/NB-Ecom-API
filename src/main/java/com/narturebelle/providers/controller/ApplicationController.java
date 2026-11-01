package com.narturebelle.providers.controller;

import com.narturebelle.providers.dto.ApplicationDtos.ApplicationResponse;
import com.narturebelle.providers.dto.ApplicationDtos.ChangeProfessionRequest;
import com.narturebelle.providers.dto.ApplicationDtos.DocumentResponse;
import com.narturebelle.providers.dto.ApplicationDtos.EntryResponse;
import com.narturebelle.providers.dto.ApplicationDtos.SaveAnswersRequest;
import com.narturebelle.providers.dto.ApplicationDtos.StartApplicationRequest;
import com.narturebelle.providers.dto.FormDtos.ApplyInfoResponse;
import com.narturebelle.providers.dto.FormDtos.FormSection;
import com.narturebelle.providers.dto.WorkspaceDtos.PublicCredentialsResponse;
import com.narturebelle.providers.exception.ProviderOnboardingException;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.model.Role;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.RateLimiter;
import com.narturebelle.providers.service.ApplicationDocumentService;
import com.narturebelle.providers.service.ApplicationService;
import com.narturebelle.providers.service.ProviderWorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

/** The applicant's application: anyone signed in can start one. Access rules are in ApplicationService. */
@RestController
@RequestMapping("/api/v1")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationDocumentService documentService;
    private final ProviderWorkspaceService workspaceService;
    private final AuthorizationService authorizationService;
    private final RateLimiter rateLimiter;

    public ApplicationController(ApplicationService applicationService, ApplicationDocumentService documentService,
                                 ProviderWorkspaceService workspaceService, AuthorizationService authorizationService, RateLimiter rateLimiter) {
        this.applicationService = applicationService;
        this.documentService = documentService;
        this.workspaceService = workspaceService;
        this.authorizationService = authorizationService;
        this.rateLimiter = rateLimiter;
    }

    // ---- public ----

    @GetMapping("/public/apply-info")
    public ResponseEntity<ApplyInfoResponse> applyInfo() {
        return ResponseEntity.ok(applicationService.applyInfo());
    }

    /** A live professional's registration details, so mothers can verify them (telemedicine display rule). */
    @GetMapping("/public/providers/{providerId}/credentials")
    public ResponseEntity<PublicCredentialsResponse> credentials(@PathVariable Long providerId) {
        return ResponseEntity.ok(workspaceService.publicCredentials(providerId));
    }

    // ---- applicant ----

    @GetMapping("/provider-application/entry")
    public ResponseEntity<EntryResponse> entry() {
        return ResponseEntity.ok(applicationService.entry());
    }

    @GetMapping("/provider-application/form")
    public ResponseEntity<List<FormSection>> form(@RequestParam Role profession) {
        return ResponseEntity.ok(applicationService.form(profession));
    }

    @GetMapping("/provider-application")
    public ResponseEntity<ApplicationResponse> current() {
        return ResponseEntity.ok(applicationService.current());
    }

    @PostMapping("/provider-application")
    public ResponseEntity<ApplicationResponse> start(@Valid @RequestBody StartApplicationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.start(req));
    }

    @PutMapping("/provider-application/profession")
    public ResponseEntity<ApplicationResponse> changeProfession(@Valid @RequestBody ChangeProfessionRequest req) {
        return ResponseEntity.ok(applicationService.changeProfession(req.profession()));
    }

    @PatchMapping("/provider-application/answers")
    public ResponseEntity<ApplicationResponse> saveAnswers(@Valid @RequestBody SaveAnswersRequest req) {
        return ResponseEntity.ok(applicationService.saveAnswers(req.answers()));
    }

    @PostMapping(value = "/provider-application/documents/{questionKey}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@PathVariable String questionKey, @RequestParam("file") MultipartFile file) {
        Long userId = authorizationService.requireUserId();
        if (!rateLimiter.tryAcquire("provider-upload:" + userId, 60, Duration.ofHours(1))) {
            throw new ProviderOnboardingException(429, "TOO_MANY_REQUESTS", "Too many uploads. Please try again later.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentResponse.from(documentService.upload(applicationService.requireCurrent(), questionKey, file)));
    }

    @DeleteMapping("/provider-application/documents/{questionKey}")
    public ResponseEntity<Void> removeDocument(@PathVariable String questionKey) {
        ProviderApplication application = applicationService.requireEditable();
        documentService.removeByApplicant(application, questionKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/provider-application/documents/{documentId}/file")
    public ResponseEntity<byte[]> download(@PathVariable Long documentId) {
        return attachment(documentService.download(applicationService.requireCurrent().getId(), documentId));
    }

    @PostMapping("/provider-application/submit")
    public ResponseEntity<ApplicationResponse> submit() {
        return ResponseEntity.ok(applicationService.submit());
    }

    @PostMapping("/provider-application/withdraw")
    public ResponseEntity<ApplicationResponse> withdraw() {
        return ResponseEntity.ok(applicationService.withdraw());
    }

    static ResponseEntity<byte[]> attachment(ApplicationDocumentService.DownloadedDocument found) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(found.document().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(found.document().getFileName()).build().toString())
                .body(found.content());
    }
}
