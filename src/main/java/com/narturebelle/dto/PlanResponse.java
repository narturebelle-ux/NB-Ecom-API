package com.narturebelle.dto;

import com.narturebelle.model.PlanCode;
import com.narturebelle.model.PlanEntitlement;
import com.narturebelle.model.PlanVersion;
import com.narturebelle.model.Role;
import com.narturebelle.service.PlanPricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * A plan priced for some number of months. GET /plans lists the full 9-month plans; GET /me/plans/quote
 * prices them for her remaining months (see PlanPricing). monthlyPrice is the listed price / 9.
 */
public class PlanResponse {

    private Long id;
    private PlanCode code;
    private String name;
    private Integer months;
    private BigDecimal monthlyPrice;
    private BigDecimal fullOneTimePrice;
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
        return forMonths(planId, code, name, version, allocations, PlanPricing.FULL_MONTHS);
    }

    public static PlanResponse forMonths(Long planId, PlanCode code, String name, PlanVersion version,
                                         List<PlanEntitlement> allocations, int months) {
        PlanResponse dto = new PlanResponse();
        dto.id = planId;
        dto.code = code;
        dto.name = name;
        dto.months = months;
        dto.monthlyPrice = PlanPricing.prorate(version.getOneTimePrice(), 1);
        dto.fullOneTimePrice = version.getOneTimePrice();
        dto.oneTimePrice = PlanPricing.prorate(version.getOneTimePrice(), months);
        dto.installmentTotalPrice = PlanPricing.prorate(version.getInstallmentTotalPrice(), months);
        dto.installmentCount = PlanPricing.installmentCount(version.getInstallmentCount(), months);
        dto.validityDays = version.getValidityDays();
        dto.inclusions = version.getInclusions();
        dto.exclusions = version.getExclusions();
        dto.allocations = allocations.stream()
                .map(a -> new AllocationItem(a.getProfessionalType(), PlanPricing.prorateSessions(a.getSessionCount(), months)))
                .toList();
        dto.totalSessions = months >= PlanPricing.FULL_MONTHS
                ? version.getTotalSessions()
                : dto.allocations.stream().mapToInt(AllocationItem::sessionCount).sum();
        return dto;
    }

    public Long getId() { return id; }
    public PlanCode getCode() { return code; }
    public String getName() { return name; }
    public Integer getMonths() { return months; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public BigDecimal getFullOneTimePrice() { return fullOneTimePrice; }
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
