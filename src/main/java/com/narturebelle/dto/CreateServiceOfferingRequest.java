package com.narturebelle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import com.narturebelle.model.Modality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CreateServiceOfferingRequest {

    @NotBlank
    @Size(max = 255) private String name;

    @Size(max = 2000) private String description;

    @NotNull
    private Modality modality;

    @NotNull
    @Positive
    @Max(480) private Integer durationMinutes;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Modality getModality() { return modality; }
    public void setModality(Modality modality) { this.modality = modality; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
}
