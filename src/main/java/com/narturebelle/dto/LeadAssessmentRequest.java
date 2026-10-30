package com.narturebelle.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.narturebelle.model.PregnancyStage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

public class LeadAssessmentRequest {
    @Size(max = 255) private String name;

    @Email
    @Size(max = 254) private String email;

    @NotBlank
    @Pattern(regexp = InputRules.PHONE, message = InputRules.PHONE_MESSAGE) private String phone;
    private PregnancyStage stage;
    private LocalDate dueDate;
    @Size(max = 255) private String city;
    @Pattern(regexp = InputRules.PINCODE, message = InputRules.PINCODE_MESSAGE) private String pincode;
    private List<String> symptoms;
    @Size(max = 255) private String leadSource;

    public LeadAssessmentRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public PregnancyStage getStage() { return stage; }
    public void setStage(PregnancyStage stage) { this.stage = stage; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public List<String> getSymptoms() { return symptoms; }
    public void setSymptoms(List<String> symptoms) { this.symptoms = symptoms; }

    public String getLeadSource() { return leadSource; }
    public void setLeadSource(String leadSource) { this.leadSource = leadSource; }
}
