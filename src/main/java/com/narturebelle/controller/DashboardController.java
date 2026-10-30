package com.narturebelle.controller;

import com.narturebelle.dto.DashboardResponse;
import com.narturebelle.model.User;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.DashboardService;
import com.narturebelle.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserProfileService userProfileService;

    public DashboardController(DashboardService dashboardService, UserProfileService userProfileService) {
        this.dashboardService = dashboardService;
        this.userProfileService = userProfileService;
    }

    @RequireConsent
    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard() {
        User user = userProfileService.loadSelfOrThrow();
        return ResponseEntity.ok(dashboardService.build(user));
    }
}
