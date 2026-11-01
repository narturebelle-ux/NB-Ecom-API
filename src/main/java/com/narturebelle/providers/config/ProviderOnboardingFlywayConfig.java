package com.narturebelle.providers.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Migrates public first, then this module's own schema with its own history table. A strategy rather than a
 * Flyway bean, because a Flyway bean would switch off Spring Boot's auto-configured one and public would stop
 * migrating. baselineOnMigrate is off so the app-wide true can't mark V1 as applied without running it.
 */
@Configuration
public class ProviderOnboardingFlywayConfig {

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.migrate();
            Flyway.configure()
                    .configuration(flyway.getConfiguration())
                    .schemas("provider_onboarding")
                    .defaultSchema("provider_onboarding")
                    .locations("classpath:db/migration_provider_onboarding")
                    .baselineOnMigrate(false)
                    .load()
                    .migrate();
        };
    }
}
