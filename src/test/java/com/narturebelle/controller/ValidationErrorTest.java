package com.narturebelle.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 0.4: a blank required field returns 400 VALIDATION_FAILED with the field named. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ValidationErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blankRequiredPhone_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/api/v1/leads/assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"phone\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.phone").exists());
    }
}
