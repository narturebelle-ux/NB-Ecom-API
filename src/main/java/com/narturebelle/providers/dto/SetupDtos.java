package com.narturebelle.providers.dto;

import com.narturebelle.providers.model.BankAccountType;
import com.narturebelle.providers.model.BankVerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Post-approval setup: agreement, bank and tax, training, profile. */
public final class SetupDtos {

    private SetupDtos() {}

    public record SetupStatusResponse(boolean agreementSigned, boolean bankAdded, BankVerificationStatus bankVerification,
                                      boolean taxCompleted, boolean trainingPassed, boolean servicesAdded, boolean slotsAdded,
                                      boolean profileConfirmed, boolean recheckRequired, boolean readyForGoLive,
                                      List<String> missing) {}

    public record AgreementResponse(String version, String text, BigDecimal feePerSession, boolean draft, boolean signingEnabled,
                                    String typedName, LocalDateTime signedAt) {}

    public record SignAgreementRequest(@NotBlank(message = "Please type your full legal name.") @Size(max = 160) String typedName) {}

    public record BankDetailsRequest(
            @NotBlank(message = "Please enter the account holder's name.") @Size(max = 160) String accountHolderName,
            @NotBlank(message = "Please enter your account number.")
            @Pattern(regexp = "^[0-9]{9,18}$", message = "Enter a 9 to 18 digit account number.") String accountNumber,
            @NotBlank(message = "Please enter your account number again.") String accountNumberConfirm,
            @NotBlank(message = "Please enter your IFSC.")
            @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Enter an IFSC like HDFC0001234.") String ifsc,
            @NotNull(message = "Please choose an account type.") BankAccountType accountType,
            boolean gstRegistered,
            @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$", message = "Enter a valid GSTIN.") String gstin) {}

    public record BankDetailsResponse(String accountHolderName, String accountLast4, String ifsc, BankAccountType accountType,
                                      boolean gstRegistered, String gstin, BankVerificationStatus verificationStatus) {}

    public record TrainingQuestion(String id, String topic, String text, List<String> options) {}

    public record TrainingResponse(List<TrainingQuestion> questions, boolean draft, LocalDateTime passedAt) {}

    public record SubmitTrainingRequest(@NotNull Map<String, Integer> answers) {}

    public record TrainingResult(boolean passed, int correct, int total) {}
}
