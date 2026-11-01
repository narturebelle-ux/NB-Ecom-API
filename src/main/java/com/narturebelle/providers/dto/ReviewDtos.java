package com.narturebelle.providers.dto;

import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.providers.model.BankAccountType;
import com.narturebelle.providers.model.BankVerificationStatus;
import com.narturebelle.providers.model.ProfileChangeStatus;
import com.narturebelle.providers.model.ProfileChangeType;
import com.narturebelle.providers.model.ReviewDecision;
import com.narturebelle.providers.model.ReviewStage;
import com.narturebelle.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Operations and Medical Reviewer screens. */
public final class ReviewDtos {

    private ReviewDtos() {}

    public record QueueItem(Long id, String applicantName, Role profession, ApplicationStatus status, LocalDateTime submittedAt,
                            List<String> flags, boolean recheckRequired) {}

    public record ReviewAnswer(String key, String label, String value) {}

    public record ReviewSection(String title, List<ReviewAnswer> answers) {}

    public record ReviewHistoryItem(ReviewStage stage, Long reviewerUserId, ReviewDecision decision, String reason,
                                    Map<String, Boolean> checklist, LocalDateTime createdAt) {}

    public record BankForReview(String accountHolderName, String accountNumber, String ifsc, BankAccountType accountType,
                                boolean gstRegistered, String gstin, BankVerificationStatus verificationStatus) {}

    public record ReviewDetail(Long id, Long userId, String email, String phone, Role profession, ApplicationStatus status,
                               List<String> flags, List<ReviewSection> sections, List<ApplicationDtos.DocumentResponse> documents,
                               List<ApplicationDtos.RequestedChangeResponse> requestedChanges, List<ReviewHistoryItem> history,
                               Long operationsReviewerId, Integer confirmedExperienceYears, List<String> clearedStages,
                               boolean canPrescribe, String registrationNumber, LocalDate licenceExpiresOn, LocalDateTime submittedAt,
                               LocalDateTime verifiedAt, boolean recheckRequired, SetupDtos.SetupStatusResponse setup, BankForReview bank,
                               List<ChangeRequestForReview> changeRequests) {}

    public record RequestedChangeInput(@NotBlank String questionKey, @Size(max = 500) String note) {}

    public record OperationsDecisionRequest(
            @NotNull(message = "Please choose a decision.") ReviewDecision decision,
            String reason, List<RequestedChangeInput> changes, boolean fraud, Map<String, Boolean> checklist) {}

    public record MedicalDecisionRequest(
            @NotNull(message = "Please choose a decision.") ReviewDecision decision,
            String reason, List<RequestedChangeInput> changes, boolean fraud, Map<String, Boolean> checklist,
            Integer confirmedExperienceYears, List<String> clearedStages) {}

    public record BankVerificationRequest(boolean passed, @Size(max = 500) String reason) {}

    public record StatusChangeRequest(@NotNull ApplicationStatus status, @NotBlank(message = "Please give a reason.") String reason) {}

    public record ChangeRequestForReview(Long id, Long applicationId, ProfileChangeType type, ProfileChangeStatus status,
                                         Map<String, Object> values, Long pendingDocumentId, LocalDateTime createdAt) {}

    public record ChangeDecisionRequest(boolean approve, @Size(max = 500) String reason) {}
}
