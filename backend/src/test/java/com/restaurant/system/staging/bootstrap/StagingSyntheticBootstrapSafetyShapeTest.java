package com.restaurant.system.staging.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.system.analytics.service.AnalyticsAggregationScheduler;
import com.restaurant.system.common.config.WebSocketConfig;
import com.restaurant.system.common.realtime.NonWebRealtimeEventPublisher;
import com.restaurant.system.common.realtime.RealtimeEventPublisher;
import com.restaurant.system.common.realtime.StompRealtimeEventPublisher;
import com.restaurant.system.printing.service.impl.OrderDispatchOutboxProcessor;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class StagingSyntheticBootstrapSafetyShapeTest {

    @Test
    void commandRequiresDedicatedProfileAndExplicitEnableProperty() {
        Profile profile = StagingSyntheticBootstrapCommand.class.getAnnotation(Profile.class);
        Profile serviceProfile = StagingSyntheticBootstrapServiceImpl.class.getAnnotation(Profile.class);
        ConditionalOnProperty condition =
            StagingSyntheticBootstrapCommand.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(profile.value()).containsExactly("staging-synthetic-bootstrap");
        assertThat(serviceProfile.value()).containsExactly("staging-synthetic-bootstrap");
        assertThat(condition.prefix()).isEqualTo("stg005.bootstrap");
        assertThat(condition.name()).containsExactly("command-enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    @Test
    void commandProfileExcludesLongLivedRuntimeComponents() {
        assertThat(AnalyticsAggregationScheduler.class.getAnnotation(Profile.class).value())
            .containsExactly("!staging-synthetic-bootstrap");
        assertThat(OrderDispatchOutboxProcessor.class.getAnnotation(Profile.class).value())
            .containsExactly("!staging-synthetic-bootstrap");
        assertThat(WebSocketConfig.class.getAnnotation(Profile.class).value())
            .containsExactly("!staging-synthetic-bootstrap");
        assertThat(StompRealtimeEventPublisher.class.getAnnotation(Profile.class).value())
            .containsExactly("!staging-synthetic-bootstrap");
        assertThat(NonWebRealtimeEventPublisher.class.getAnnotation(Profile.class).value())
            .containsExactly("staging-synthetic-bootstrap");
    }

    @Test
    void nonWebProfileProvidesExactlyOneRealtimePublisherWithoutWebSocketInfrastructure() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("staging-synthetic-bootstrap");
            context.register(StompRealtimeEventPublisher.class, NonWebRealtimeEventPublisher.class);
            context.refresh();

            assertThat(context.getBeansOfType(RealtimeEventPublisher.class))
                .containsOnlyKeys("nonWebRealtimeEventPublisher");
        }
    }

    @Test
    void v9IsAppendOnlyAndContainsNoSyntheticDataOrSecrets() throws Exception {
        String migration = Files.readString(Path.of(
            "src/main/resources/db/migration/V9__add_staging_synthetic_bootstrap_requests.sql"
        ));

        assertThat(migration)
            .contains("CREATE TABLE public.staging_synthetic_bootstrap_requests")
            .contains("CONSTRAINT uq_staging_synthetic_bootstrap_run_id UNIQUE (run_id)")
            .doesNotContainIgnoringCase("INSERT INTO")
            .doesNotContainIgnoringCase("UPDATE ")
            .doesNotContainIgnoringCase("DELETE FROM")
            .doesNotContainIgnoringCase("password")
            .doesNotContainIgnoringCase("token")
            .doesNotContainIgnoringCase("printer");
    }
}
