package com.restaurant.system.staging.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restaurant.system.staging.bootstrap.StagingSyntheticBootstrapExecutionContext;
import com.restaurant.system.staging.bootstrap.StagingSyntheticBootstrapGuard;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StagingSyntheticSourceMenuGuardTest {

    private static final String RUNTIME_SHA = "4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c";
    private static final String TOOL_SHA = "1111111111111111111111111111111111111111";
    private final StagingSyntheticSourceMenuGuard guard = new StagingSyntheticSourceMenuGuard(
        new StagingSyntheticBootstrapGuard()
    );

    @Test
    void acceptsExactStagingIdentityAndSyntheticSourceStore() {
        guard.validate(context("DISABLED", false), spec());
    }

    @Test
    void rejectsSourceStoreOtherThanReviewedProfileSource() {
        assertRejected(
            new StagingSyntheticSourceMenuSpec(2L, "STG005_SRC_R01", RUNTIME_SHA, TOOL_SHA),
            "STG005_SOURCE_MENU_STORE_REJECTED"
        );
    }

    @Test
    void rejectsNonSyntheticSourceStoreCode() {
        assertRejected(
            new StagingSyntheticSourceMenuSpec(1L, "ST_DENIS", RUNTIME_SHA, TOOL_SHA),
            "STG005_SOURCE_MENU_STORE_REJECTED"
        );
    }

    @Test
    void retainsPrintingDisabledEnvironmentGuard() {
        assertThatThrownBy(() -> guard.validate(context("PAD_DIRECT", true), spec()))
            .isInstanceOfSatisfying(
                StagingSyntheticSourceMenuException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_BOOTSTRAP_PRINTING_REJECTED")
            );
    }

    @Test
    void rejectsRuntimeAndToolBindingDrift() {
        assertRejected(
            new StagingSyntheticSourceMenuSpec(1L, "STG005_SRC_R01", "2222222222222222222222222222222222222222", TOOL_SHA),
            "STG005_BOOTSTRAP_RUNTIME_SHA_MISMATCH"
        );
        assertRejected(
            new StagingSyntheticSourceMenuSpec(1L, "STG005_SRC_R01", RUNTIME_SHA, "2222222222222222222222222222222222222222"),
            "STG005_BOOTSTRAP_TOOL_SHA_MISMATCH"
        );
    }

    private void assertRejected(StagingSyntheticSourceMenuSpec requested, String errorCode) {
        assertThatThrownBy(() -> guard.validate(context("DISABLED", false), requested))
            .isInstanceOfSatisfying(
                StagingSyntheticSourceMenuException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode)
            );
    }

    private StagingSyntheticSourceMenuSpec spec() {
        return new StagingSyntheticSourceMenuSpec(1L, "STG005_SRC_R01", RUNTIME_SHA, TOOL_SHA);
    }

    private StagingSyntheticBootstrapExecutionContext context(
        String printingMode,
        boolean printingEnabled
    ) {
        return new StagingSyntheticBootstrapExecutionContext(
            Set.of("cloud", "staging-synthetic-bootstrap"),
            "restaurant-pos-staging",
            "/srv/restaurant-pos/staging",
            RUNTIME_SHA,
            RUNTIME_SHA,
            TOOL_SHA,
            printingMode,
            printingEnabled,
            "jdbc:postgresql://db:5432/restaurant_pos_staging",
            "restaurant_pos_staging",
            "none"
        );
    }
}
