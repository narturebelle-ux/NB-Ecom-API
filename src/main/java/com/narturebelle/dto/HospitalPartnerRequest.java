package com.narturebelle.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Admin create/replace for a HospitalPartner. PUT replaces every field, so send the full record. */
public class HospitalPartnerRequest {

    @NotBlank @Size(max = 255) private String name;
    @NotBlank @Size(max = 255) private String city;
    @Size(max = 20) private String pincode;
    @Size(max = 255) private String address;
    @Size(max = 255) private String specialty;
    @DecimalMin("0.0") @DecimalMax("5.0") private Double rating;
    @PositiveOrZero private Integer reviewCount;
    @Size(max = 50) private String phone;
    @Size(max = 255) private String consultationUrl;
    @Size(max = 255) private String discountDetails;
    @Size(max = 255) private String partnerTier;
    @Size(max = 255) private String imageUrl;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getConsultationUrl() { return consultationUrl; }
    public void setConsultationUrl(String consultationUrl) { this.consultationUrl = consultationUrl; }
    public String getDiscountDetails() { return discountDetails; }
    public void setDiscountDetails(String discountDetails) { this.discountDetails = discountDetails; }
    public String getPartnerTier() { return partnerTier; }
    public void setPartnerTier(String partnerTier) { this.partnerTier = partnerTier; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
