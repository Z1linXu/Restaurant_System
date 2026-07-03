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

    private MockEnvironment pilotEnvironment() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.auth.jwt-secret", SAFE_SECRET)
            .withProperty("app.seed.default-users-enabled", "false")
            .withProperty("app.seed.demo-data-enabled", "false");
        environment.setActiveProfiles("pilot");
        return environment;
    }
}
