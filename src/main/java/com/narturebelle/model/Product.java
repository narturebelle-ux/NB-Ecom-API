package com.narturebelle.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    private BigDecimal originalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PregnancyStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NecessityTier tier;

    @Column(length = 1500)
    private String clinicalRationale;

    private String imageUrl;

    private Double rating;

    private Integer reviewCount;

    private Boolean doctorApproved;

    private String category; // e.g., "Nutrition", "Sleep Comfort", "Body Care", "Labor Prep"

    @ElementCollection
    @CollectionTable(name = "product_benefits", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "benefit")
    private List<String> keyBenefits = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "product_symptoms", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "symptom")
    private List<String> targetSymptoms = new ArrayList<>();

    public Product() {}

    public Product(Long id, String name, String description, BigDecimal price, BigDecimal originalPrice,
                   PregnancyStage stage, NecessityTier tier, String clinicalRationale, String imageUrl,
                   Double rating, Integer reviewCount, Boolean doctorApproved, String category,
                   List<String> keyBenefits, List<String> targetSymptoms) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.originalPrice = originalPrice;
        this.stage = stage;
        this.tier = tier;
        this.clinicalRationale = clinicalRationale;
        this.imageUrl = imageUrl;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.doctorApproved = doctorApproved;
        this.category = category;
        this.keyBenefits = keyBenefits;
        this.targetSymptoms = targetSymptoms;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }

    public PregnancyStage getStage() { return stage; }
    public void setStage(PregnancyStage stage) { this.stage = stage; }

    public NecessityTier getTier() { return tier; }
    public void setTier(NecessityTier tier) { this.tier = tier; }

    public String getClinicalRationale() { return clinicalRationale; }
    public void setClinicalRationale(String clinicalRationale) { this.clinicalRationale = clinicalRationale; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public Boolean getDoctorApproved() { return doctorApproved; }
    public void setDoctorApproved(Boolean doctorApproved) { this.doctorApproved = doctorApproved; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getKeyBenefits() { return keyBenefits; }
    public void setKeyBenefits(List<String> keyBenefits) { this.keyBenefits = keyBenefits; }

    public List<String> getTargetSymptoms() { return targetSymptoms; }
    public void setTargetSymptoms(List<String> targetSymptoms) { this.targetSymptoms = targetSymptoms; }
}
