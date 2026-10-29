package com.narturebelle.dto;

import com.narturebelle.model.Provider;
import com.narturebelle.model.Role;

import java.util.List;

/** Public-safe fields only — never credentials or the linked userId. */
public class ProviderResponse {

    private Long id;
    private Role professionalType;
    private String displayName;
    private String bio;
    private String city;
    private List<String> languages;

    public static ProviderResponse from(Provider provider) {
        ProviderResponse dto = new ProviderResponse();
        dto.id = provider.getId();
        dto.professionalType = provider.getProfessionalType();
        dto.displayName = provider.getDisplayName();
        dto.bio = provider.getBio();
        dto.city = provider.getCity();
        dto.languages = provider.getLanguages();
        return dto;
    }

    public Long getId() { return id; }
    public Role getProfessionalType() { return professionalType; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public String getCity() { return city; }
    public List<String> getLanguages() { return languages; }
}
