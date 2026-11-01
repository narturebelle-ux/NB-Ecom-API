package com.narturebelle.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tells whether a provider joined through NB-Ecom-Providers-API, which owns the provider_onboarding schema in this
 * same database. Read directly, because admin status changes need the answer even when that service is asleep.
 * Before that service's first deploy the table doesn't exist, and no provider is managed.
 */
@Component
public class ProviderOnboardingGuard {

    private final JdbcTemplate jdbcTemplate;

    public ProviderOnboardingGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean managedByOnboarding(Long providerId) {
        Integer tables = jdbcTemplate.queryForObject("select count(*) from information_schema.tables"
                + " where lower(table_schema) = 'provider_onboarding' and lower(table_name) = 'provider_applications'", Integer.class);
        if (tables == null || tables == 0) {
            return false;
        }
        Integer applications = jdbcTemplate.queryForObject(
                "select count(*) from provider_onboarding.provider_applications where provider_id = ?", Integer.class, providerId);
        return applications != null && applications > 0;
    }
}
