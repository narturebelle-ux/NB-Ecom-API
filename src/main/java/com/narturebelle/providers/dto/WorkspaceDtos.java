package com.narturebelle.providers.dto;

import com.narturebelle.model.Modality;
import com.narturebelle.providers.model.ProfileChangeStatus;
import com.narturebelle.providers.model.ProfileChangeType;
import com.narturebelle.model.ProviderStatus;
import com.narturebelle.model.Role;
import com.narturebelle.model.SlotStatus;
import com.narturebelle.model.ProviderSlot;
import com.narturebelle.model.ServiceOffering;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** The professional's dashboard: services, availability, earnings, profile, product activity. */
public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    public record ServiceRequest(
            @NotBlank(message = "Please name the service.") @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @NotNull(message = "Please choose a mode.") Modality modality,
            @NotNull(message = "Please enter a length.") @Min(value = 10, message = "A session is at least 10 minutes.")
            @Max(value = 180, message = "A session is at most 180 minutes.") Integer durationMinutes) {}

    public record ServiceResponse(Long id, String name, String description, Modality modality, Integer durationMinutes, boolean active) {
        public static ServiceResponse from(ServiceOffering s) {
            return new ServiceResponse(s.getId(), s.getName(), s.getDescription(), s.getModality(), s.getDurationMinutes(), s.isActive());
        }
    }

    public record SlotWindow(@NotNull LocalDateTime startAt, @NotNull LocalDateTime endAt) {}

    public record SlotsRequest(@NotEmpty(message = "Please add at least one slot.") @Size(max = 50) @Valid List<SlotWindow> slots) {}

    public record SlotResponse(Long id, LocalDateTime startAt, LocalDateTime endAt, SlotStatus status) {
        public static SlotResponse from(ProviderSlot s) {
            return new SlotResponse(s.getId(), s.getStartAt(), s.getEndAt(), s.getStatus());
        }
    }

    public record EarningItem(Long bookingId, BigDecimal amount, String status, LocalDateTime createdAt) {}

    /** paidOutTotal is always zero today: payouts are recorded but no bank transfer exists yet. */
    public record EarningsResponse(BigDecimal feePerSession, BigDecimal totalEarned, BigDecimal paidOutTotal, int completedSessions,
                                   boolean feeIsDraft, List<EarningItem> items) {}

    /** Deliberately no booking ID, mother's ID or exact time, so feedback can't be traced back to a mother. */
    public record FeedbackItem(Integer rating, String comment, java.time.LocalDate date) {}

    public record FeedbackResponse(int count, Double averageRating, List<FeedbackItem> items) {}

    public record ProductActivityItem(String productName, String priority, String note, LocalDateTime createdAt) {}

    public record ProfileResponse(Long providerId, Role profession, ProviderStatus providerStatus, String legalName, String displayName,
                                  String bio, String city, List<String> languages, String council, String registrationNumber,
                                  LocalDate licenceExpiresOn, Integer confirmedExperienceYears, List<String> clearedStages,
                                  boolean canPrescribe, SetupDtos.BankDetailsResponse bank) {}

    public record ChangeRequestBody(@NotNull(message = "Please choose what to change.") ProfileChangeType type,
                                    @NotNull Map<String, Object> values) {}

    public record ChangeRequestResponse(Long id, ProfileChangeType type, ProfileChangeStatus status, String decisionReason,
                                        LocalDateTime createdAt, LocalDateTime decidedAt, boolean documentRequired, boolean documentAttached) {}

    /** Public: what the telemedicine guidelines require mothers to be able to see and verify. */
    public record PublicCredentialsResponse(Role profession, String council, String registrationNumber, Integer confirmedExperienceYears) {}
}
