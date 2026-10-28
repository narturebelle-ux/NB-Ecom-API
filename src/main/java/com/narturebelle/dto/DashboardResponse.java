package com.narturebelle.dto;

import com.narturebelle.model.User;

import java.util.List;

public class DashboardResponse {

    private ProfileSummary profile;
    private List<String> pendingConsents;
    private JourneyResponse journey;
    private List<NextActionResponse> nextActions;
    private boolean journeySetupRequired;

    public static DashboardResponse noJourney(User user, List<String> pendingConsents) {
        DashboardResponse dto = new DashboardResponse();
        dto.profile = ProfileSummary.from(user);
        dto.pendingConsents = pendingConsents;
        dto.journey = null;
        dto.nextActions = List.of();
        dto.journeySetupRequired = true;
        return dto;
    }

    public static DashboardResponse withJourney(User user, List<String> pendingConsents, JourneyResponse journey,
                                                  List<NextActionResponse> nextActions) {
        DashboardResponse dto = new DashboardResponse();
        dto.profile = ProfileSummary.from(user);
        dto.pendingConsents = pendingConsents;
        dto.journey = journey;
        dto.nextActions = nextActions;
        dto.journeySetupRequired = false;
        return dto;
    }

    public ProfileSummary getProfile() { return profile; }
    public List<String> getPendingConsents() { return pendingConsents; }
    public JourneyResponse getJourney() { return journey; }
    public List<NextActionResponse> getNextActions() { return nextActions; }
    public boolean isJourneySetupRequired() { return journeySetupRequired; }
}
