package com.narturebelle.dto;

import com.narturebelle.model.IdeaCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateIdeaRequest {

    @NotNull private IdeaCategory category;
    @NotBlank @Size(max = 120) private String title;
    @NotBlank @Size(max = 1500) private String description;
    /** OK to show her name on the Idea Impact section if the idea is implemented. */
    private boolean featureConsent;

    public IdeaCategory getCategory() { return category; }
    public void setCategory(IdeaCategory category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isFeatureConsent() { return featureConsent; }
    public void setFeatureConsent(boolean featureConsent) { this.featureConsent = featureConsent; }
}
