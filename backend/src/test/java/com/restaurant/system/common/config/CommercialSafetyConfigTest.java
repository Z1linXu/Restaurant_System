package com.restaurant.system.common.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CommercialSafetyConfigTest {
    MockEnvironment safe() {
        var env = new MockEnvironment().withProperty("app.auth.jwt-secret", "synthetic-only-unit-test-secret-12345678")
            .withProperty("app.environment", "commercial-local")
            .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:55439/commercial_synthetic")
            .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
            .withProperty("spring.flyway.enabled", "true").withProperty("spring.flyway.validate-on-migrate", "true")
            .withProperty("app.seed.runtime-enabled", "false")
            .withProperty("app.printing.allowed-modes", "DISABLED,MOCK")
            .withProperty("app.printing.endpoint-configuration-enabled", "false");
        env.setActiveProfiles("commercial"); return env;
    }
    @Test void explicitIsolatedConfigurationPasses() {
        assertThatCode(() -> ProductionSafetyConfig.validateEnvironment(safe())).doesNotThrowAnyException();
    }
    @Test void unsafeOverridesCannotEnableRealIoSeedOrDeveloperIdentity() {
        var cases = java.util.Map.of(
            "app.auth.x-user-id-fallback-enabled", "true", "app.seed.demo-data-enabled", "true",
            "app.seed.runtime-enabled", "true", "spring.jpa.hibernate.ddl-auto", "update",
            "app.printing.allowed-modes", "DISABLED,PAD_DIRECT", "app.printing.endpoint-configuration-enabled", "true",
            "UBER_EATS_ENABLED", "true", "spring.flyway.validate-on-migrate", "false",
            "spring.datasource.url", "jdbc:postgresql://db:5432/restaurant_system", "app.environment", "production");
        cases.forEach((key, value) -> assertThatThrownBy(() -> ProductionSafetyConfig.validateEnvironment(safe().withProperty(key, value)))
            .as(key).isInstanceOf(IllegalStateException.class));
        var mixed = safe(); mixed.setActiveProfiles("commercial", "local");
        assertThatThrownBy(() -> ProductionSafetyConfig.validateEnvironment(mixed)).isInstanceOf(IllegalStateException.class);
    }
}
