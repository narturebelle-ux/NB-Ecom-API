package com.narturebelle.dto;

import com.narturebelle.model.User;

public class AuthResponse {
    private boolean success;
    private String token;
    private String message;
    private User user;

    public AuthResponse() {}

    public AuthResponse(boolean success, String token, String message, User user) {
        this.success = success;
        this.token = token;
        this.message = message;
        this.user = user;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
