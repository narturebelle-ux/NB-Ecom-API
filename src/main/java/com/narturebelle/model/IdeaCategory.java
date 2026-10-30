package com.narturebelle.model;

/** The five top categories every idea in the pool belongs to. Labels are what the UI shows. */
public enum IdeaCategory {
    CARE_AND_SESSIONS("Care & sessions", "Experts, bookings, check-ups and the care team"),
    NUTRITION_AND_WELLNESS("Nutrition & wellness", "Food, fitness, sleep and mental well-being"),
    BABY_AND_POSTPARTUM("Baby & postpartum", "Life after birth: baby care, recovery and feeding"),
    COMMUNITY("Community & Joy Circle", "Events, groups and connecting with other moms"),
    APP_EXPERIENCE("App & experience", "Making NartureBelle easier and more delightful to use");

    private final String label;
    private final String description;

    IdeaCategory(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}
