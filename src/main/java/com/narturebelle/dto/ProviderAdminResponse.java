package com.narturebelle.dto;

import com.narturebelle.model.Provider;
import com.narturebelle.model.ProviderCredential;
import com.narturebelle.model.ProviderStatus;
import com.narturebelle.model.Role;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Admin view — adds userId, raw status, and credentials on top of the public ProviderResponse fields. */
public class ProviderAdminResponse {

    private Long id;
    private Long userId;
    private Role professionalType;
    private String displayName;
    private String bio;
    private String city;
    private List<String> languages;
    private ProviderStatus status;
    private List<CredentialItem> credentials;

    public static ProviderAdminResponse from(Provider provider, List<ProviderCredential> credentials) {
        ProviderAdminResponse dto = new ProviderAdminResponse();
        dto.id = provider.getId();
        dto.userId = provider.getUserId();
        dto.professionalType = provider.getProfessionalType();
        dto.displayName = provider.getDisplayName();
        dto.bio = provider.getBio();
        dto.city = provider.getCity();
        dto.languages = provider.getLanguages();
        dto.status = provider.getStatus();
        dto.credentials = credentials.stream()
                .map(c -> new CredentialItem(c.getId(), c.getCredentialType(), c.getDocumentReference(),
                        c.getIssuedAt(), c.getExpiresAt(), c.getVerifiedAt()))
                .toList();
        return dto;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Role getProfessionalType() { return professionalType; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public String getCity() { return city; }
    public List<String> getLanguages() { return languages; }
    public ProviderStatus getStatus() { return status; }
    public List<CredentialItem> getCredentials() { return credentials; }

    public record CredentialItem(Long id, String credentialType, String documentReference,
                                  LocalDate issuedAt, LocalDate expiresAt, LocalDateTime verifiedAt) {}
}
