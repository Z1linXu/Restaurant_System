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
            .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
            .withProperty("spring.flyway.enabled", "true");
        environment.setActiveProfiles("cloud");
        return environment;
    }
}
