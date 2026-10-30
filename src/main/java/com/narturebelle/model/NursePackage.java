package com.narturebelle.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nurse_packages")
public class NursePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // e.g. "Trimester Care Companion", "Full Pregnancy & Birth Doula", "Monthly On-Call Nurse"

    private String tagLine; // e.g. "Dedicated senior female nurse on call for your daily pregnancy questions"

    @Column(nullable = false)
    private BigDecimal price;

    private BigDecimal originalPrice;

    private String duration; // e.g. "Per Trimester (3 Months)", "Monthly Subscription", "Full 9 Months"

    private String badge; // e.g. "MOST POPULAR", "COMPLETE PEACE OF MIND"

    @Column(length = 1500)
    private String description;

    // EAGER like every other element collection here: the controller returns this entity directly, and a
    // lazy collection threw LazyInitializationException (500) once the session had closed.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nurse_package_features", joinColumns = @JoinColumn(name = "package_id"))
    @Column(name = "feature")
    private List<String> features = new ArrayList<>();

    public NursePackage() {}

    public NursePackage(Long id, String name, String tagLine, BigDecimal price, BigDecimal originalPrice,
                        String duration, String badge, String description, List<String> features) {
        this.id = id;
        this.name = name;
        this.tagLine = tagLine;
        this.price = price;
        this.originalPrice = originalPrice;
        this.duration = duration;
        this.badge = badge;
        this.description = description;
        this.features = features;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTagLine() { return tagLine; }
    public void setTagLine(String tagLine) { this.tagLine = tagLine; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }
}
