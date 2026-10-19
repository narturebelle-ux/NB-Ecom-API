package com.narturebelle.dto;

import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.User;

import java.time.LocalDate;

/** Response shape for /api/v1/auth/firebase and /api/v1/users/** per the AUTH CONTRACT. */
public class UserProfileResponse {

    private Long id;
    private String firebaseUid;
    private String email;
    private Boolean emailVerified;
    private String phone;
    private Boolean phoneVerified;
    private String firstName;
    private String lastName;
    private PregnancyStage stage;
    private LocalDate dueDate;
    private String role;

    public static UserProfileResponse from(User user) {
        UserProfileResponse dto = new UserProfileResponse();
        dto.id = user.getId();
        dto.firebaseUid = user.getFirebaseUid();
        dto.email = user.getEmail();
        dto.emailVerified = user.getEmailVerified();
        dto.phone = user.getPhone();
        dto.phoneVerified = user.getPhoneVerified();
        dto.firstName = user.getFirstName();
        dto.lastName = user.getLastName();
        dto.stage = user.getStage();
        dto.dueDate = user.getDueDate();
        dto.role = user.getRole();
        return dto;
    }

    public Long getId() { return id; }
    public String getFirebaseUid() { return firebaseUid; }
    public String getEmail() { return email; }
    public Boolean getEmailVerified() { return emailVerified; }
    public String getPhone() { return phone; }
    public Boolean getPhoneVerified() { return phoneVerified; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public PregnancyStage getStage() { return stage; }
    public LocalDate getDueDate() { return dueDate; }
    public String getRole() { return role; }
}
