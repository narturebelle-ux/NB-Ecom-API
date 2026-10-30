package com.narturebelle.dto;

import com.narturebelle.model.DeletionRequestStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateDeletionRequestStatusRequest {

    @NotNull
    private DeletionRequestStatus status;

    public DeletionRequestStatus getStatus() { return status; }
    public void setStatus(DeletionRequestStatus status) { this.status = status; }
}
