package com.narturebelle.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.narturebelle.model.AccountDeletionRequest;
import com.narturebelle.model.DeletionRequestStatus;

import java.time.LocalDateTime;

public class DeletionRequestResponse {

    private Long id;
    private DeletionRequestStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime updatedAt;

    /** Admin view only -- omitted from the user's own responses. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long userId;

    public static DeletionRequestResponse from(AccountDeletionRequest request) {
        DeletionRequestResponse dto = new DeletionRequestResponse();
        dto.id = request.getId();
        dto.status = request.getStatus();
        dto.requestedAt = request.getRequestedAt();
        dto.updatedAt = request.getUpdatedAt();
        return dto;
    }

    public static DeletionRequestResponse forAdmin(AccountDeletionRequest request) {
        DeletionRequestResponse dto = from(request);
        dto.userId = request.getUserId();
        return dto;
    }

    public Long getId() { return id; }
    public DeletionRequestStatus getStatus() { return status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getUserId() { return userId; }
}
