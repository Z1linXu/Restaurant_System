package com.restaurant.system.owner.provisioning;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhaseBProvisioningRuntimeGateTest {

    @Test
    void cloudProfileNeedsAnExplicitStagingRuntimeMarker() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.phase-b.provisioning.enabled", "true")
            .withProperty("app.phase-b.runtime", "disabled");

        assertThrows(RuntimeException.class, () -> new PhaseBProvisioningRuntimeGate(environment).requireEnabled());
    }

    @Test
    void stagingMarkerAndGateAreRequiredTogether() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.phase-b.provisioning.enabled", "true")
            .withProperty("app.phase-b.runtime", "staging")
            .withProperty("app.environment", "staging");

        assertDoesNotThrow(() -> new PhaseBProvisioningRuntimeGate(environment).requireEnabled());
    }

    @Test
    void productionProfileRemainsForbiddenEvenWithStagingMarker() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.phase-b.provisioning.enabled", "true")
            .withProperty("app.phase-b.runtime", "staging")
            .withProperty("app.environment", "staging");
        environment.setActiveProfiles("cloud", "production");

        assertThrows(RuntimeException.class, () -> new PhaseBProvisioningRuntimeGate(environment).requireEnabled());
    }

    @Test
    void stagingMarkerWithoutImmutableEnvironmentMarkerIsForbidden() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.phase-b.provisioning.enabled", "true")
            .withProperty("app.phase-b.runtime", "staging");

        assertThrows(RuntimeException.class, () -> new PhaseBProvisioningRuntimeGate(environment).requireEnabled());
    }
}
