package com.restaurant.system.common.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyConfigTest {

    private static final String SAFE_SECRET = "cloud-production-secret-value-1234567890";

    @Test
    void localProfileAllowsDevSecretAndXUserFallback() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.auth.jwt-secret", "dev-local-restaurant-pos-change-this-secret-please-2026")
            .withProperty("app.auth.x-user-id-fallback-enabled", "true")
            .withProperty("app.dev-tools.role-switcher-enabled", "true")
            .withProperty("app.seed.default-users-enabled", "true")
            .withProperty("app.seed.demo-data-enabled", "true")
            .withProperty("app.seed.membership-supplement-enabled", "true")
            .withProperty("spring.jpa.hibernate.ddl-auto", "update")
            .withProperty("spring.flyway.enabled", "false");
        environment.setActiveProfiles("local");

        assertDoesNotThrow(() -> ProductionSafetyConfig.validateEnvironment(environment));
    }

    @Test
    void cloudProfileWithSafeConfigPasses() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET);

        assertDoesNotThrow(() -> ProductionSafetyConfig.validateEnvironment(environment));
    }

    @Test
    void cloudProfileWithXUserFallbackFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("app.auth.x-user-id-fallback-enabled", "true");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("app.auth.x-user-id-fallback-enabled"));
    }

    @Test
    void cloudProfileWithDevRoleSwitcherFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("app.dev-tools.role-switcher-enabled", "true");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("app.dev-tools.role-switcher-enabled"));
    }

    @Test
    void cloudProfileWithSeedForceOverwriteFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("app.seed.force-overwrite", "true");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("app.seed.force-overwrite"));
    }

    @Test
    void cloudProfileWithDefaultUsersSeedFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("app.seed.default-users-enabled", "true");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("app.seed.default-users-enabled"));
    }

    @Test
    void cloudProfileWithDemoDataSeedFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("app.seed.demo-data-enabled", "true");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("app.seed.demo-data-enabled"));
    }

    @Test
    void cloudProfileWithMembershipSupplementSeedFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("app.seed.membership-supplement-enabled", "true");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("app.seed.membership-supplement-enabled"));
    }

    @Test
    void cloudProfileWithProductionBootstrapSeedFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("app.seed.production-bootstrap-enabled", "true");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("app.seed.production-bootstrap-enabled"));
    }

    @Test
    void cloudProfileWithDevSecretFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", "dev-local-restaurant-pos-change-this-secret-please-2026");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("dev-local"));
    }

    @Test
    void cloudProfileWithPlaceholderSecretFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", "replace-this-cloud-secret-before-production-use");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("replace-this"));
    }

    @Test
    void cloudProfileWithTooShortSecretFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", "short-secret");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("at least 32"));
    }

    @Test
    void cloudProfileWithDdlAutoUpdateFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("spring.jpa.hibernate.ddl-auto", "update");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("spring.jpa.hibernate.ddl-auto"));
    }

    @Test
    void cloudProfileWithFlywayDisabledFails() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("spring.flyway.enabled", "false");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("spring.flyway.enabled"));
    }

    @Test
    void guardedStagingSyntheticBootstrapOneShotAllowsFlywayDisabled() {
        MockEnvironment environment = guardedStagingSyntheticOneShotEnvironment();

        assertDoesNotThrow(() -> ProductionSafetyConfig.validateEnvironment(environment));
    }

    @Test
    void guardedStagingSyntheticSourceMenuOneShotAllowsFlywayDisabled() {
        MockEnvironment environment = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("stg005.bootstrap.command-enabled", "false")
            .withProperty("stg005.source-menu.command-enabled", "true");

        assertDoesNotThrow(() -> ProductionSafetyConfig.validateEnvironment(environment));
    }

    @Test
    void guardedStagingSyntheticOneShotRequiresFlywayDisabled() {
        MockEnvironment environment = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("spring.flyway.enabled", "true");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("must be false"));
    }

    @Test
    void guardedStagingSyntheticOneShotRequiresDdlValidate() {
        MockEnvironment ddlNone = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("spring.jpa.hibernate.ddl-auto", "none");
        MockEnvironment ddlBlank = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("spring.jpa.hibernate.ddl-auto", "");

        assertSyntheticViolation(ddlNone, "spring.jpa.hibernate.ddl-auto");
        assertSyntheticViolation(ddlBlank, "spring.jpa.hibernate.ddl-auto");
    }

    @Test
    void stagingSyntheticOneShotRejectsExtraProductionProfile() {
        MockEnvironment environment = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("spring.flyway.enabled", "true");
        environment.setActiveProfiles("cloud", "staging-synthetic-bootstrap", "production");

        assertSyntheticViolation(environment, "requires exactly the cloud");
    }

    @Test
    void stagingSyntheticOneShotRequiresCloudProfile() {
        MockEnvironment environment = guardedStagingSyntheticOneShotEnvironment();
        environment.setActiveProfiles("staging-synthetic-bootstrap");

        assertSyntheticViolation(environment, "requires exactly the cloud");
    }

    @Test
    void stagingSyntheticOneShotRetainsOrdinaryCloudSafetyGuards() {
        MockEnvironment environment = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("app.auth.x-user-id-fallback-enabled", "true");

        assertSyntheticViolation(environment, "app.auth.x-user-id-fallback-enabled");
    }

    @Test
    void stagingSyntheticOneShotRequiresNonWebMode() {
        MockEnvironment environment = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("spring.main.web-application-type", "servlet")
            .withProperty("spring.flyway.enabled", "true");

        assertSyntheticViolation(environment, "spring.main.web-application-type");
    }

    @Test
    void stagingSyntheticOneShotRequiresExactlyOneCommand() {
        MockEnvironment noCommand = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("stg005.bootstrap.command-enabled", "false");
        MockEnvironment bothCommands = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("stg005.source-menu.command-enabled", "true");

        assertSyntheticViolation(noCommand, "exactly one guarded STG-005");
        assertSyntheticViolation(bothCommands, "exactly one guarded STG-005");
    }

    @Test
    void stagingSyntheticOneShotRequiresPrintingAndSeedGuards() {
        MockEnvironment printingEnabled = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("app.features.printing", "true");
        MockEnvironment runtimeSeedEnabled = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("app.seed.runtime-enabled", "true");

        assertSyntheticViolation(printingEnabled, "app.features.printing");
        assertSyntheticViolation(runtimeSeedEnabled, "app.seed.runtime-enabled");
    }

    @Test
    void stagingSyntheticOneShotRequiresExactDatabaseIdentity() {
        MockEnvironment wrongDatabase = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/restaurant_system");
        MockEnvironment uppercaseDatabase = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://DB:5432/restaurant_pos_staging");
        MockEnvironment paddedDatabase = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("spring.datasource.url", " jdbc:postgresql://db:5432/restaurant_pos_staging ");
        MockEnvironment wrongDatabaseUser = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("spring.datasource.username", "postgres");
        MockEnvironment uppercaseDatabaseUser = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("spring.datasource.username", "RESTAURANT_POS_STAGING");
        MockEnvironment paddedDatabaseUser = guardedStagingSyntheticOneShotEnvironment()
            .withProperty("spring.datasource.username", " restaurant_pos_staging ");

        assertSyntheticViolation(wrongDatabase, "spring.datasource.url");
        assertSyntheticViolation(uppercaseDatabase, "spring.datasource.url");
        assertSyntheticViolation(paddedDatabase, "spring.datasource.url");
        assertSyntheticViolation(wrongDatabaseUser, "spring.datasource.username");
        assertSyntheticViolation(uppercaseDatabaseUser, "spring.datasource.username");
        assertSyntheticViolation(paddedDatabaseUser, "spring.datasource.username");
    }

    @Test
    void pilotProfileWithPlaceholderSecretFails() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.auth.jwt-secret", "replace-this-pilot-secret-before-production-use");
        environment.setActiveProfiles("pilot");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("replace-this"));
    }

    @Test
    void pilotProfileWithDefaultUsersSeedFails() {
        MockEnvironment environment = pilotEnvironment()
            .withProperty("app.seed.default-users-enabled", "true");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("app.seed.default-users-enabled"));
    }

    @Test
    void pilotProfileWithDemoDataSeedFails() {
        MockEnvironment environment = pilotEnvironment()
            .withProperty("app.seed.demo-data-enabled", "true");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("app.seed.demo-data-enabled"));
    }

    @Test
    void pilotProfileWithDevSecretFails() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.auth.jwt-secret", "dev-local-restaurant-pos-change-this-secret-please-2026");
        environment.setActiveProfiles("pilot");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("dev-local"));
    }

    @Test
    void pilotProfileWithTooShortSecretFails() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.auth.jwt-secret", "short-secret");
        environment.setActiveProfiles("pilot");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains("at least 32"));
    }

    private MockEnvironment cloudEnvironment() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.auth.x-user-id-fallback-enabled", "false")
            .withProperty("app.dev-tools.role-switcher-enabled", "false")
            .withProperty("app.seed.force-overwrite", "false")
            .withProperty("app.seed.default-users-enabled", "false")
            .withProperty("app.seed.demo-data-enabled", "false")
            .withProperty("app.seed.membership-supplement-enabled", "false")
            .withProperty("app.seed.production-bootstrap-enabled", "false")
            .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
            .withProperty("spring.flyway.enabled", "true");
        environment.setActiveProfiles("cloud");
        return environment;
    }

    private MockEnvironment guardedStagingSyntheticOneShotEnvironment() {
        MockEnvironment environment = cloudEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("spring.flyway.enabled", "false")
            .withProperty("spring.main.web-application-type", "none")
            .withProperty("app.features.printing", "false")
            .withProperty("app.seed.runtime-enabled", "false")
            .withProperty("stg005.bootstrap.command-enabled", "true")
            .withProperty("stg005.source-menu.command-enabled", "false")
            .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/restaurant_pos_staging")
            .withProperty("spring.datasource.username", "restaurant_pos_staging");
        environment.setActiveProfiles("cloud", "staging-synthetic-bootstrap");
        return environment;
    }

    private void assertSyntheticViolation(MockEnvironment environment, String field) {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ProductionSafetyConfig.validateEnvironment(environment)
        );

        assertTrue(exception.getMessage().contains(field));
    }

    private MockEnvironment pilotEnvironment() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("app.seed.default-users-enabled", "false")
            .withProperty("app.seed.demo-data-enabled", "false");
        environment.setActiveProfiles("pilot");
        return environment;
    }
}
