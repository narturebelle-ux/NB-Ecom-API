package com.narturebelle.dto;

import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Response shape for GET/PATCH /api/v1/me — same fields as UserProfileResponse plus
 * pendingConsents/hasActiveJourney. Kept as its own class rather than extending
 * UserProfileResponse: /auth/firebase's response contract doesn't include these two fields, and
 * duplicating a handful of getters here is simpler than forcing an inheritance relationship.
 */
public class MeProfileResponse {

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
    private List<String> roles;
    private List<String> pendingConsents;
    private boolean hasActiveJourney;

    public static MeProfileResponse from(User user, List<String> pendingConsents, boolean hasActiveJourney) {
        MeProfileResponse dto = new MeProfileResponse();
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
        dto.role = user.getRoles().stream()
                .min(Comparator.comparing(Enum::name))
                .map(Role::name)
                .orElse(user.getRole());
        dto.pendingConsents = pendingConsents;
        dto.hasActiveJourney = hasActiveJourney;
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
    public List<String> getPendingConsents() { return pendingConsents; }
    public boolean isHasActiveJourney() { return hasActiveJourney; }
}
