package com.narturebelle.dto;

import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

/** multipart/form-data body of POST /careers/jobs/{id}/applications. The résumé is checked in CareersService. */
public class JobApplicationForm {

    @NotBlank @Size(max = 120) private String fullName;
    @NotBlank @Email @Size(max = 254) private String email;
    @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,20}", message = "must be a phone number of 7-20 digits") private String phone;
    @Size(max = 120) private String location;
    @Size(max = 300) @Pattern(regexp = "(https?://\\S+)?", message = "must be an http(s) link") private String linkedinUrl;
    @Size(max = 300) @Pattern(regexp = "(https?://\\S+)?", message = "must be an http(s) link") private String portfolioUrl;
    @Min(0) @Max(50) private Integer yearsExperience;
    @Size(max = 3000) private String coverNote;
    @NotNull @AssertTrue(message = "must be accepted") private Boolean privacyConsent;
    private MultipartFile resume;
    /** Honeypot: hidden in the UI, so only bots fill it. */
    private String website;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public String getPortfolioUrl() { return portfolioUrl; }
    public void setPortfolioUrl(String portfolioUrl) { this.portfolioUrl = portfolioUrl; }
    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }
    public String getCoverNote() { return coverNote; }
    public void setCoverNote(String coverNote) { this.coverNote = coverNote; }
    public Boolean getPrivacyConsent() { return privacyConsent; }
    public void setPrivacyConsent(Boolean privacyConsent) { this.privacyConsent = privacyConsent; }
    public MultipartFile getResume() { return resume; }
    public void setResume(MultipartFile resume) { this.resume = resume; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
}
