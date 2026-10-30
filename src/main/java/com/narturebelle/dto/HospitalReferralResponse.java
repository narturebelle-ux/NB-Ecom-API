package com.narturebelle.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.narturebelle.model.HospitalReferral;
import com.narturebelle.model.ReferralStatus;

import java.time.LocalDateTime;

public class HospitalReferralResponse {

    private Long id;
    private String code;
    private Long hospitalId;
    private String hospitalName;
    private ReferralStatus status;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;

    /** Admin view only -- omitted from the user's own responses. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long userId;

    public static HospitalReferralResponse from(HospitalReferral referral, String hospitalName) {
        HospitalReferralResponse dto = new HospitalReferralResponse();
        dto.id = referral.getId();
        dto.code = referral.getCode();
        dto.hospitalId = referral.getHospitalId();
        dto.hospitalName = hospitalName;
        dto.status = referral.getStatus();
        dto.note = referral.getNote();
        dto.createdAt = referral.getCreatedAt();
        dto.confirmedAt = referral.getConfirmedAt();
        return dto;
    }

    public static HospitalReferralResponse forAdmin(HospitalReferral referral, String hospitalName) {
        HospitalReferralResponse dto = from(referral, hospitalName);
        dto.userId = referral.getUserId();
        return dto;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Long getHospitalId() { return hospitalId; }
    public String getHospitalName() { return hospitalName; }
    public ReferralStatus getStatus() { return status; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public Long getUserId() { return userId; }
}
