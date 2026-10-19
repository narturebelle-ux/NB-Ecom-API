package com.narturebelle.model;

import jakarta.persistence.*;

@Entity
@Table(name = "hospital_partners")
public class HospitalPartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    private String pincode;

    private String address;

    private String specialty; // e.g., "Maternity & High-Risk Obstetrics", "4D Fetal Ultrasound & Genetics", "Natural Birthing Center"

    private Double rating;

    private Integer reviewCount;

    private String phone;

    private String consultationUrl;

    private String discountDetails; // e.g., "Free First Ultrasound + 10% Off Delivery Packages"

    private String partnerTier; // e.g., "PLATINUM_ACCREDITED", "VERIFIED_PARTNER"

    private String imageUrl;

    public HospitalPartner() {}

    public HospitalPartner(Long id, String name, String city, String pincode, String address, String specialty,
                           Double rating, Integer reviewCount, String phone, String consultationUrl,
                           String discountDetails, String partnerTier, String imageUrl) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.pincode = pincode;
        this.address = address;
        this.specialty = specialty;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.phone = phone;
        this.consultationUrl = consultationUrl;
        this.discountDetails = discountDetails;
        this.partnerTier = partnerTier;
        this.imageUrl = imageUrl;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
