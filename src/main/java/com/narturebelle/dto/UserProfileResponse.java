package com.narturebelle.dto;

import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

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
    /** @deprecated kept for one release so older UI code doesn't break — use {@link #roles}. */
    @Deprecated
    private String role;
    private List<String> roles;

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
        dto.roles = user.getRoles().stream().map(Role::name).sorted().toList();
        // "Primary" role for backward compat: alphabetically first of the new roles, falling back
        // to the legacy column only if a row somehow has no roles at all.
        dto.role = user.getRoles().stream()
                .min(Comparator.comparing(Enum::name))
                .map(Role::name)
                .orElse(user.getRole());
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
    @Deprecated
    public String getRole() { return role; }
    public List<String> getRoles() { return roles; }
}
