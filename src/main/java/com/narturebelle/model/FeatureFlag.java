package com.narturebelle.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    /**
     * Natural key, e.g. "nurseCompanion" — also the JSON field name GET /api/v1/features exposes it
     * under. Column name is "flag_key", not "key" — KEY is a reserved SQL word in both H2 and Postgres.
     */
    @Id
    @Column(name = "flag_key")
    private String key;

    private boolean enabled;

    private String description;

    private LocalDateTime updatedAt;

    private String updatedBy;

    public FeatureFlag() {}

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
