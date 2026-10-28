package com.narturebelle.dto;

import com.narturebelle.model.User;

public class ProfileSummary {

    private String firstName;
    private String lastName;

    public static ProfileSummary from(User user) {
        ProfileSummary dto = new ProfileSummary();
        dto.firstName = user.getFirstName();
        dto.lastName = user.getLastName();
        return dto;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
}
