package com.narturebelle.dto;

import com.narturebelle.model.PlanCode;
import com.narturebelle.model.PlanEntitlement;
import com.narturebelle.model.PlanVersion;
import com.narturebelle.model.Role;

import java.math.BigDecimal;
import java.util.List;

public class PlanResponse {

    private Long id;
    private PlanCode code;
    private String name;
    private BigDecimal oneTimePrice;
    private BigDecimal installmentTotalPrice;
    private Integer installmentCount;
    private Integer totalSessions;
    private Integer validityDays;
    private List<String> inclusions;
    private List<String> exclusions;
    private List<AllocationItem> allocations;

    public static PlanResponse from(Long planId, PlanCode code, String name, PlanVersion version,
                                     List<PlanEntitlement> allocations) {
        PlanResponse dto = new PlanResponse();
        dto.id = planId;
        dto.code = code;
        dto.name = name;
        dto.oneTimePrice = version.getOneTimePrice();
        dto.installmentTotalPrice = version.getInstallmentTotalPrice();
        dto.installmentCount = version.getInstallmentCount();
        dto.totalSessions = version.getTotalSessions();
        dto.validityDays = version.getValidityDays();
        dto.inclusions = version.getInclusions();
        dto.exclusions = version.getExclusions();
        dto.allocations = allocations.stream()
                .map(a -> new AllocationItem(a.getProfessionalType(), a.getSessionCount()))
                .toList();
        return dto;
    }

    public Long getId() { return id; }
    public PlanCode getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getOneTimePrice() { return oneTimePrice; }
    public BigDecimal getInstallmentTotalPrice() { return installmentTotalPrice; }
    public Integer getInstallmentCount() { return installmentCount; }
    public Integer getTotalSessions() { return totalSessions; }
    public Integer getValidityDays() { return validityDays; }
    public List<String> getInclusions() { return inclusions; }
    public List<String> getExclusions() { return exclusions; }
    public List<AllocationItem> getAllocations() { return allocations; }

    public record AllocationItem(Role professionalType, Integer sessionCount) {}
}
