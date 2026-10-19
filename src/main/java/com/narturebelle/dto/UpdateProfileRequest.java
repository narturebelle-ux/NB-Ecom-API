package com.narturebelle.dto;

/**
 * Request body for PUT /api/v1/users/{id}
 * All fields are optional — only non-null values are applied.
 */
public class UpdateProfileRequest {

    private String firstName;
    private String lastName;
    private String phone;
    private String stage;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
}
