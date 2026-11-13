package com.narturebelle.providers.dto;

import com.narturebelle.model.FileKind;
import com.narturebelle.model.ScanVerdict;
import com.narturebelle.providers.model.ApplicationDocument;
import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.model.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ApplicationDtos {

    private ApplicationDtos() {}

    public record StartApplicationRequest(
            @NotNull(message = "Please choose your profession.") Role profession,
            @AssertTrue(message = "Please accept the application terms.") boolean termsAccepted,
            @AssertTrue(message = "Please consent to the verification checks.") boolean verificationConsent) {}

    public record ChangeProfessionRequest(@NotNull(message = "Please choose your profession.") Role profession) {}

    public record SaveAnswersRequest(@NotNull Map<String, Object> answers) {}

    /**
     * kind is DOCUMENT or VIDEO; scanStatus is null for older files kept in Postgres. available is false while
     * an S3 file waits for its malware scan or was blocked by it. Built by ApplicationDocumentService.toResponse.
     */
    public record DocumentResponse(Long id, String questionKey, String fileName, int sizeBytes, LocalDateTime uploadedAt,
                                   FileKind kind, String mimeType, ScanVerdict scanStatus, boolean available) {
        public static DocumentResponse from(ApplicationDocument d, boolean available) {
            return new DocumentResponse(d.getId(), d.getQuestionKey(), d.getFileName(), d.getSizeBytes(), d.getUploadedAt(),
                    d.getKind(), d.getContentType(), d.getScanStatus(), available);
        }
    }

    public record RequestedChangeResponse(String questionKey, String note) {}

    public record MissingAnswer(String key, String label, String section) {}

    /** The applicant's own view. Sensitive answers are never returned; sensitiveSaved says which are on file. */
    public record ApplicationResponse(
            Long id, Role profession, ApplicationStatus status, Map<String, Object> answers, List<String> sensitiveSaved,
            List<DocumentResponse> documents, List<RequestedChangeResponse> requestedChanges, LocalDateTime submittedAt,
            String rejectionReason, LocalDate reapplyAfter, LocalDateTime verifiedAt, LocalDateTime activatedAt,
            boolean canPrescribe, Integer confirmedExperienceYears, Long providerId, LocalDate licenceExpiresOn,
            boolean licenceLapsed, boolean contactVerified) {}

    /** What the sign-in routing needs: whether this account belongs in the professional area, and why not if blocked. */
    public record EntryResponse(boolean hasApplication, ApplicationStatus status, boolean motherAccount, boolean blocked,
                                LocalDate reapplyAfter, boolean emailVerified, boolean phoneVerified) {}
}
