package com.narturebelle.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * application/json body of POST /careers/jobs/{id}/applications: the same fields and checks as
 * JobApplicationForm, with the résumé already uploaded straight to S3 (POST .../files/upload-url first).
 */
public record JobApplicationJsonRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,20}", message = "must be a phone number of 7-20 digits") String phone,
        @Size(max = 120) String location,
        @Size(max = 300) @Pattern(regexp = "(https?://\\S+)?", message = "must be an http(s) link") String linkedinUrl,
        @Size(max = 300) @Pattern(regexp = "(https?://\\S+)?", message = "must be an http(s) link") String portfolioUrl,
        @Min(0) @Max(50) Integer yearsExperience,
        @Size(max = 3000) String coverNote,
        @NotNull @AssertTrue(message = "must be accepted") Boolean privacyConsent,
        @NotNull(message = "Please attach your résumé.") @Valid DirectUploadRequests.SavedFile resume,
        /** Honeypot: hidden in the UI, so only bots fill it. */
        String website) {
}
