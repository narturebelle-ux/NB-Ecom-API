package com.narturebelle.dto;

import jakarta.validation.constraints.Size;
import com.narturebelle.model.Role;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CreateProviderRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Role professionalType;

    @Size(max = 255) private String displayName;
    @Size(max = 2000) private String bio;
    @Size(max = 255) private String city;
    private List<String> languages;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Role getProfessionalType() { return professionalType; }
    public void setProfessionalType(Role professionalType) { this.professionalType = professionalType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }
}
