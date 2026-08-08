package com.restaurant.system.staging.menu;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

class StagingSyntheticSourceMenuSafetyShapeTest {

    @Test
    void commandRequiresExistingDedicatedProfileAndExplicitProperty() {
        Profile profile = StagingSyntheticSourceMenuCommand.class.getAnnotation(Profile.class);
        Profile guardProfile = StagingSyntheticSourceMenuGuard.class.getAnnotation(Profile.class);
        Profile factoryProfile = StagingSyntheticSourceMenuManifestFactory.class.getAnnotation(Profile.class);
        Profile plannerProfile = StagingSyntheticSourceMenuPlanner.class.getAnnotation(Profile.class);
        ConditionalOnProperty condition =
            StagingSyntheticSourceMenuCommand.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(profile.value()).containsExactly("staging-synthetic-bootstrap");
        assertThat(guardProfile.value()).containsExactly("staging-synthetic-bootstrap");
        assertThat(factoryProfile.value()).containsExactly("staging-synthetic-bootstrap");
        assertThat(plannerProfile.value()).containsExactly("staging-synthetic-bootstrap");
        assertThat(condition.prefix()).isEqualTo("stg005.source-menu");
        assertThat(condition.name()).containsExactly("command-enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }
}
