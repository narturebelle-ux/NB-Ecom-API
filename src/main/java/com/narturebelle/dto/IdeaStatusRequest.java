package com.narturebelle.dto;

import com.narturebelle.model.IdeaStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin moves an idea along. impactNote (what changed, shown publicly) is required for IMPLEMENTED. */
public class IdeaStatusRequest {

    @NotNull private IdeaStatus status;
    @Size(max = 1000) private String impactNote;

    public IdeaStatus getStatus() { return status; }
    public void setStatus(IdeaStatus status) { this.status = status; }
    public String getImpactNote() { return impactNote; }
    public void setImpactNote(String impactNote) { this.impactNote = impactNote; }
}
