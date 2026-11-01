package com.narturebelle.controller;

import com.narturebelle.security.FirebaseTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The websites allowed to call this API from a browser, including the pre-production one. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsOriginsTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    @Test
    void productionAndPreProductionSitesAreAllowed() throws Exception {
        for (String origin : new String[]{"https://www.narturebelle.com", "https://narturebelle.com", "https://staging.narturebelle.com"}) {
            mockMvc.perform(options("/api/v1/plans").header("Origin", origin).header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
    }

    @Test
    void otherSitesAreRefused() throws Exception {
        mockMvc.perform(options("/api/v1/plans").header("Origin", "https://staging.example.com").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
