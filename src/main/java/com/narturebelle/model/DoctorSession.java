package com.narturebelle.model;

import jakarta.persistence.*;

@Entity
@Table(name = "doctor_sessions")
public class DoctorSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String doctorName;

    private String doctorTitle; // e.g. "Senior Consultant Obstetrician & Gynecologist", "Fetal Medicine Specialist"

    private String hospitalAffiliation; // e.g. "Cloudnine Hospital, Bengaluru", "Apollo Cradle, Mumbai"

    @Column(nullable = false)
    private String topic;

    @Column(length = 1500)
    private String description;

    @Enumerated(EnumType.STRING)
    private PregnancyStage targetStage; // e.g. MONTH_3, MONTH_6, MONTH_9

    private String sessionDate; // e.g. "Tomorrow, 6:00 PM IST"

    private Integer durationMinutes;

    private Integer attendeeCount;

    private String bannerUrl;

    private String meetingUrl;

    private String status; // "UPCOMING", "LIVE", "RECORDED"

    public DoctorSession() {}

    public DoctorSession(Long id, String doctorName, String doctorTitle, String hospitalAffiliation,
                         String topic, String description, PregnancyStage targetStage, String sessionDate,
                         Integer durationMinutes, Integer attendeeCount, String bannerUrl,
                         String meetingUrl, String status) {
        this.id = id;
        this.doctorName = doctorName;
        this.doctorTitle = doctorTitle;
        this.hospitalAffiliation = hospitalAffiliation;
        this.topic = topic;
        this.description = description;
        this.targetStage = targetStage;
        this.sessionDate = sessionDate;
        this.durationMinutes = durationMinutes;
        this.attendeeCount = attendeeCount;
        this.bannerUrl = bannerUrl;
        this.meetingUrl = meetingUrl;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getDoctorTitle() { return doctorTitle; }
    public void setDoctorTitle(String doctorTitle) { this.doctorTitle = doctorTitle; }

    public String getHospitalAffiliation() { return hospitalAffiliation; }
    public void setHospitalAffiliation(String hospitalAffiliation) { this.hospitalAffiliation = hospitalAffiliation; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public PregnancyStage getTargetStage() { return targetStage; }
    public void setTargetStage(PregnancyStage targetStage) { this.targetStage = targetStage; }

    public String getSessionDate() { return sessionDate; }
    public void setSessionDate(String sessionDate) { this.sessionDate = sessionDate; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public Integer getAttendeeCount() { return attendeeCount; }
    public void setAttendeeCount(Integer attendeeCount) { this.attendeeCount = attendeeCount; }

    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }

    public String getMeetingUrl() { return meetingUrl; }
    public void setMeetingUrl(String meetingUrl) { this.meetingUrl = meetingUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
