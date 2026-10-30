package com.narturebelle.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.narturebelle.model.Coordinator;

import java.util.List;

/** User-facing summary (on call slots and the booked call): id, displayName, languages, photoUrl.
 * The admin view also carries bio and active. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoordinatorResponse {

    private Long id;
    private String displayName;
    private List<String> languages;
    private String photoUrl;
    private String bio;
    private Boolean active;

    public static CoordinatorResponse summary(Coordinator coordinator) {
        CoordinatorResponse dto = new CoordinatorResponse();
        dto.id = coordinator.getId();
        dto.displayName = coordinator.getDisplayName();
        dto.languages = List.copyOf(coordinator.getLanguages());
        dto.photoUrl = coordinator.getPhotoUrl();
        return dto;
    }

    public static CoordinatorResponse forAdmin(Coordinator coordinator) {
        CoordinatorResponse dto = summary(coordinator);
        dto.bio = coordinator.getBio();
        dto.active = coordinator.isActive();
        return dto;
    }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public List<String> getLanguages() { return languages; }
    public String getPhotoUrl() { return photoUrl; }
    public String getBio() { return bio; }
    public Boolean getActive() { return active; }
}
