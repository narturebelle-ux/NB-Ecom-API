package com.narturebelle.dto;

import com.narturebelle.model.CareProtocolStep;
import com.narturebelle.model.ReviewStatus;
import com.narturebelle.model.Role;

public class NextActionResponse {

    private Role professionalType;
    private String title;
    private String description;
    private ReviewStatus reviewStatus;
    private boolean optional;

    public static NextActionResponse from(CareProtocolStep step) {
        NextActionResponse dto = new NextActionResponse();
        dto.professionalType = step.getProfessionalType();
        dto.title = step.getTitle();
        dto.description = step.getDescription();
        dto.reviewStatus = step.getReviewStatus();
        dto.optional = step.isOptional();
        return dto;
    }

    public Role getProfessionalType() { return professionalType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public boolean isOptional() { return optional; }
}
