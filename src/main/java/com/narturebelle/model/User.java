package com.narturebelle.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;

    /** Kept for backward-compat; auto-built from firstName + lastName on save */
    private String name;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phone;

    @Column(unique = true)
    private String firebaseUid;

    private Boolean emailVerified = false;

    private Boolean phoneVerified = false;

    private String authProvider; // "GOOGLE", "PHONE", "LOCAL", "FIREBASE_DEV"

    @Enumerated(EnumType.STRING)
    private PregnancyStage stage;

    private LocalDate dueDate;

    private String role; // "USER", "VIP_MEMBER", "ADMIN"

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.role == null) {
            this.role = "USER";
        }
        syncName();
    }

    @PreUpdate
    protected void onUpdate() {
        syncName();
    }

    /** Keeps legacy `name` field in sync with firstName + lastName */
    private void syncName() {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        if (!first.isEmpty() || !last.isEmpty()) {
            this.name = (first + " " + last).trim();
        }
    }

    public User() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getName() { return name; }
    public void setName(String name) {
        this.name = name;
        // If firstName/lastName not set yet, split name for display
        if (name != null && firstName == null && lastName == null) {
            String[] parts = name.trim().split("\\s+", 2);
            this.firstName = parts[0];
            this.lastName = parts.length > 1 ? parts[1] : "";
        }
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getFirebaseUid() { return firebaseUid; }
    public void setFirebaseUid(String firebaseUid) { this.firebaseUid = firebaseUid; }

    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }

    public Boolean getPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(Boolean phoneVerified) { this.phoneVerified = phoneVerified; }

    public String getAuthProvider() { return authProvider; }
    public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }

    public PregnancyStage getStage() { return stage; }
    public void setStage(PregnancyStage stage) { this.stage = stage; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

