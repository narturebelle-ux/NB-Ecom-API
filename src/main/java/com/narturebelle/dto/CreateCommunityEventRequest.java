package com.narturebelle.dto;

import jakarta.validation.constraints.Pattern;
import com.narturebelle.model.Modality;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class CreateCommunityEventRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 2000)
    private String description;

    @NotBlank
    @Size(max = 255)
    private String hostName;

    @Size(max = 1000)
    private String hostBio;

    @Size(max = 255)
    private String category;

    @NotNull
    @Future
    private LocalDateTime startsAt;

    @NotNull
    private LocalDateTime endsAt;

    @NotNull
    private Modality modality;

    @Size(max = 255)
    private String location;

    @Size(max = 255)
    @Pattern(regexp = InputRules.HTTPS_URL, message = InputRules.HTTPS_URL_MESSAGE) private String joinUrl;

    /** Omit for unlimited. */
    @Positive
    private Integer capacity;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }

    public String getHostBio() { return hostBio; }
    public void setHostBio(String hostBio) { this.hostBio = hostBio; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }

    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }

    public Modality getModality() { return modality; }
    public void setModality(Modality modality) { this.modality = modality; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getJoinUrl() { return joinUrl; }
    public void setJoinUrl(String joinUrl) { this.joinUrl = joinUrl; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
}
