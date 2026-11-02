package com.narturebelle.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** /health/db is public, so a failure must not reveal the database error. */
class HealthControllerTest {

    @Test
    void aDatabaseFailure_returns503_withoutTheErrorDetails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new CannotGetJdbcConnectionException("Connection to secret-host.example failed"));

        ResponseEntity<Map<String, Object>> response = new HealthController(jdbcTemplate).checkDatabase();

        assertEquals(503, response.getStatusCode().value());
        assertEquals(Map.of("database", "error"), response.getBody());
    }

    @Test
    void aHealthyDatabase_returnsOk() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        ResponseEntity<Map<String, Object>> response = new HealthController(jdbcTemplate).checkDatabase();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("database", "ok"), response.getBody());
    }
}
