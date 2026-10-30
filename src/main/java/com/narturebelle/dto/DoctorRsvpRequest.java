package com.narturebelle.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.narturebelle.model.PregnancyStage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class DoctorRsvpRequest {
    private Long sessionId;

    @NotBlank
    @Size(max = 255) private String motherName;

    @NotBlank
    @Pattern(regexp = InputRules.PHONE, message = InputRules.PHONE_MESSAGE) private String phone;

    @Email
    @Size(max = 254) private String email;
    private PregnancyStage stage;
    @Size(max = 255) private String userQuestion;

    public DoctorRsvpRequest() {}

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getMotherName() { return motherName; }
    public void setMotherName(String motherName) { this.motherName = motherName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public PregnancyStage getStage() { return stage; }
    public void setStage(PregnancyStage stage) { this.stage = stage; }

    public String getUserQuestion() { return userQuestion; }
    public void setUserQuestion(String userQuestion) { this.userQuestion = userQuestion; }
}
