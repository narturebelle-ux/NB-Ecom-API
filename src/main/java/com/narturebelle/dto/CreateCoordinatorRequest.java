package com.narturebelle.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CreateCoordinatorRequest {

    @NotBlank @Size(max = 255) private String displayName;
    @NotEmpty @Size(max = 10) private List<@NotBlank @Size(max = 50) String> languages;
    @Size(max = 255) @Pattern(regexp = InputRules.HTTPS_URL, message = InputRules.HTTPS_URL_MESSAGE) private String photoUrl;
    @Size(max = 1000) private String bio;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}
