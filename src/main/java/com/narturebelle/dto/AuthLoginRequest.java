package com.narturebelle.dto;

import com.narturebelle.model.PregnancyStage;
import jakarta.validation.constraints.Email;
import java.time.LocalDate;

public class AuthLoginRequest {
    private String provider; // "GOOGLE", "PHONE", "EMAIL"

    @Email
    private String email;
    private String phone;
    private String name;
    private String idToken; // for Google
    private String otp;     // for Phone
    private PregnancyStage stage;
    private LocalDate dueDate;

    public AuthLoginRequest() {}

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public PregnancyStage getStage() { return stage; }
    public void setStage(PregnancyStage stage) { this.stage = stage; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
}
