package com.narturebelle.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;
    private final String version;

    public HealthController(JdbcTemplate jdbcTemplate, @Value("${app.version}") String version) {
        this.jdbcTemplate = jdbcTemplate;
        this.version = version;
    }

    /** The one request to send after a deployment (D44). No database call, so it shows only that the application is up. */
    @GetMapping({"/health", "/"})
    public Map<String, String> status() {
        return Map.of("status", "UP", "version", version);
    }

    /** For manual checks only, not deployment checks. */
    @GetMapping("/health/db")
    public ResponseEntity<Map<String, Object>> checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("database", "ok"));
        } catch (Exception e) {
            // This endpoint is public, so the details stay in the log: they can name hosts and users.
            log.error("Database health check failed", e);
            return ResponseEntity.status(503).body(Map.of("database", "error"));
        }
    }
}
