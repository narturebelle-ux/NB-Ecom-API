package com.narturebelle.dto;

import com.narturebelle.model.Entitlement;
import com.narturebelle.model.PlanCode;
import com.narturebelle.model.Role;

import java.time.LocalDateTime;

public class EntitlementResponse {

    private Long id;
    private Long orderId;
    private Role professionalType;
    private Integer totalSessions;
    private Integer usedSessions;
    private Integer remainingSessions;
    private LocalDateTime expiresAt;
    private PlanCode planCode;

    public static EntitlementResponse from(Entitlement entitlement, PlanCode planCode) {
        EntitlementResponse dto = new EntitlementResponse();
        dto.id = entitlement.getId();
        dto.orderId = entitlement.getOrderId();
        dto.professionalType = entitlement.getProfessionalType();
        dto.totalSessions = entitlement.getTotalSessions();
        dto.usedSessions = entitlement.getUsedSessions();
        dto.remainingSessions = entitlement.getTotalSessions() - entitlement.getUsedSessions();
        dto.expiresAt = entitlement.getExpiresAt();
        dto.planCode = planCode;
        return dto;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Role getProfessionalType() { return professionalType; }
    public Integer getTotalSessions() { return totalSessions; }
    public Integer getUsedSessions() { return usedSessions; }
    public Integer getRemainingSessions() { return remainingSessions; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public PlanCode getPlanCode() { return planCode; }
}
