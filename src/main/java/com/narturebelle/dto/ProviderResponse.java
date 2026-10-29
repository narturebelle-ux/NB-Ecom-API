package com.narturebelle.dto;

import com.narturebelle.model.Modality;
import com.narturebelle.model.Provider;
import com.narturebelle.model.Role;
import com.narturebelle.model.ServiceOffering;

import java.util.List;

/** Public-safe fields only — never credentials or the linked userId. */
public class ProviderResponse {

    private Long id;
    private Role professionalType;
    private String displayName;
    private String bio;
    private String city;
    private List<String> languages;
    private List<ServiceOfferingItem> services;

    public static ProviderResponse from(Provider provider, List<ServiceOffering> activeOfferings) {
        ProviderResponse dto = new ProviderResponse();
        dto.id = provider.getId();
        dto.professionalType = provider.getProfessionalType();
        dto.displayName = provider.getDisplayName();
        dto.bio = provider.getBio();
        dto.city = provider.getCity();
        dto.languages = provider.getLanguages();
        dto.services = activeOfferings.stream()
                .map(o -> new ServiceOfferingItem(o.getId(), o.getName(), o.getDescription(), o.getModality(), o.getDurationMinutes()))
                .toList();
        return dto;
    }

    public Long getId() { return id; }
    public Role getProfessionalType() { return professionalType; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public String getCity() { return city; }
    public List<String> getLanguages() { return languages; }
    public List<ServiceOfferingItem> getServices() { return services; }

    public record ServiceOfferingItem(Long id, String name, String description, Modality modality, Integer durationMinutes) {}
}
