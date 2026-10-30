package com.narturebelle.dto;

import jakarta.validation.constraints.*;

public class FeedbackRequest {

    @Size(max = 120) private String name;
    @Email @Size(max = 254) private String email;
    @NotBlank @Size(max = 3000) private String message;
    @Min(1) @Max(5) private Integer rating;
    @Size(max = 300) private String page;
    /** Honeypot: hidden in the UI, so only bots fill it. */
    private String website;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getPage() { return page; }
    public void setPage(String page) { this.page = page; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
}
