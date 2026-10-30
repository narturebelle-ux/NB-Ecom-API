package com.narturebelle.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 2.1/2.2: GET /plans and /plans/{id} are public and return the seeded tiers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listPlans_noAuthNeeded_returnsSeededTiers() throws Exception {
        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("STANDARD", "CARE", "PREMIUM")))
                .andExpect(jsonPath("$[0].code").value("STANDARD"))
                .andExpect(jsonPath("$[0].oneTimePrice").value(9999.00))
                .andExpect(jsonPath("$[0].totalSessions").value(9))
                .andExpect(jsonPath("$[0].allocations").isArray());
    }

    @Test
    void getOne_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/plans/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOne_knownPlan_returnsAllocationDetail() throws Exception {
        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/plans/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists());
    }
}
