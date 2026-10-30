package com.narturebelle.dto;

import jakarta.validation.constraints.Pattern;
import com.narturebelle.model.NecessityTier;
import com.narturebelle.model.PregnancyStage;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Admin create/replace for a Product. PUT replaces every field, so send the full record. tier is still
 * required: recommendations use it internally (SKIP is never recommended) even though it's never shown. */
public class ProductRequest {

    @NotBlank @Size(max = 255) private String name;
    @Size(max = 2000) private String description;
    @NotNull @PositiveOrZero private BigDecimal price;
    @PositiveOrZero private BigDecimal originalPrice;
    @NotNull private PregnancyStage stage;
    @NotNull private NecessityTier tier;
    @Size(max = 1500) private String clinicalRationale;
    @Size(max = 255) @Pattern(regexp = InputRules.HTTPS_URL, message = InputRules.HTTPS_URL_MESSAGE) private String imageUrl;
    @DecimalMin("0.0") @DecimalMax("5.0") private Double rating;
    @PositiveOrZero private Integer reviewCount;
    private Boolean doctorApproved;
    @Size(max = 255) private String category;
    private List<@NotBlank String> keyBenefits = new ArrayList<>();
    private List<@NotBlank String> targetSymptoms = new ArrayList<>();
    /** Lower-cased on save; referred to by product health rules (e.g. "high-sugar", "sugar-free"). */
    private List<@NotBlank @Size(max = 50) String> tags = new ArrayList<>();

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

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
