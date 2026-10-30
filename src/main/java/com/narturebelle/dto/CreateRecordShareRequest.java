package com.narturebelle.dto;

import com.narturebelle.model.ShareTargetType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Cross-field "exactly one target" validation happens in the service, not via bean validation — same convention as every other cross-field check in this codebase. */
public class CreateRecordShareRequest {

    @NotNull
    private ShareTargetType shareTargetType;

    private Long targetProviderId;

    private Long targetBookingId;

    @NotNull
    private LocalDateTime expiresAt;

    public ShareTargetType getShareTargetType() { return shareTargetType; }
    public void setShareTargetType(ShareTargetType shareTargetType) { this.shareTargetType = shareTargetType; }

    public Long getTargetProviderId() { return targetProviderId; }
    public void setTargetProviderId(Long targetProviderId) { this.targetProviderId = targetProviderId; }

    public Long getTargetBookingId() { return targetBookingId; }
    public void setTargetBookingId(Long targetBookingId) { this.targetBookingId = targetBookingId; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
