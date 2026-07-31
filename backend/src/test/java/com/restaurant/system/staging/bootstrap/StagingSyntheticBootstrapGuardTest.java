package com.restaurant.system.staging.bootstrap;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class StagingSyntheticBootstrapGuardTest {

    private static final String RUNTIME_SHA = "4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c";
    private static final String TOOL_SHA = "1111111111111111111111111111111111111111";

    private final StagingSyntheticBootstrapGuard guard = new StagingSyntheticBootstrapGuard();

    @Test
    void acceptsOnlyExactStagingIdentityWithPrintingDisabled() {
        assertThatCode(() -> guard.validate(validContext(), validSpec())).doesNotThrowAnyException();
    }

    @Test
    void rejectsProductionOrNonBootstrapProfiles() {
        assertRejected(
            context(Set.of("cloud", "production", "staging-synthetic-bootstrap"), null, null, null, null),
            "STG005_BOOTSTRAP_PROFILE_REJECTED"
        );
        assertRejected(
            context(Set.of("local", "staging-synthetic-bootstrap"), null, null, null, null),
            "STG005_BOOTSTRAP_PROFILE_REJECTED"
        );
    }

    @Test
    void rejectsWrongComposeProject() {
        assertRejected(
            context(null, "cloud", null, null, null),
            "STG005_BOOTSTRAP_PROJECT_REJECTED"
        );
    }

    @Test
    void rejectsWrongStagingRoot() {
        assertRejected(
            context(null, null, "/home/ubuntu/Restaurant_System", null, null),
            "STG005_BOOTSTRAP_ROOT_REJECTED"
        );
    }

    @Test
    void rejectsRuntimeShaMismatch() {
        StagingSyntheticBootstrapExecutionContext context = new StagingSyntheticBootstrapExecutionContext(
            Set.of("cloud", "staging-synthetic-bootstrap"),
            "restaurant-pos-staging",
            "/srv/restaurant-pos/staging",
            RUNTIME_SHA,
            "2222222222222222222222222222222222222222",
            TOOL_SHA,
            "DISABLED",
            false,
            "jdbc:postgresql://db:5432/restaurant_pos_staging",
            "restaurant_pos_staging",
            "none"
        );
        assertRejected(context, "STG005_BOOTSTRAP_RUNTIME_SHA_MISMATCH");
    }

    @Test
    void rejectsPrintingModeOrFeatureThatCanProduceOutput() {
        assertRejected(
            context(null, null, null, "MOCK", false),
            "STG005_BOOTSTRAP_PRINTING_REJECTED"
        );
        assertRejected(
            context(null, null, null, "DISABLED", true),
            "STG005_BOOTSTRAP_PRINTING_REJECTED"
        );
    }

    @Test
    void rejectsNonStagingDatabaseIdentity() {
        StagingSyntheticBootstrapExecutionContext context = new StagingSyntheticBootstrapExecutionContext(
            Set.of("cloud", "staging-synthetic-bootstrap"),
            "restaurant-pos-staging",
            "/srv/restaurant-pos/staging",
            RUNTIME_SHA,
            RUNTIME_SHA,
            TOOL_SHA,
            "DISABLED",
            false,
            "jdbc:postgresql://db:5432/restaurant_pos",
            "restaurant_pos",
            "none"
        );
        assertRejected(context, "STG005_BOOTSTRAP_DATABASE_REJECTED");
    }

    @Test
    void rejectsAnyNonStg005Name() {
        StagingSyntheticBootstrapSpec invalid = new StagingSyntheticBootstrapSpec(
            "STG005_20260730_R01",
            "Real Restaurant",
            "STG005_ORG_20260730_R01",
            "STG005_SRC_20260730_R01",
            "STG005_SRC_20260730_R01",
            "STG005_OWNER_20260730_R01",
            "STG005_OWNER_20260730_R01",
            RUNTIME_SHA,
            TOOL_SHA
        );

        assertThatThrownBy(() -> guard.validate(validContext(), invalid))
            .isInstanceOfSatisfying(
                StagingSyntheticBootstrapException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_BOOTSTRAP_NAME_REJECTED")
            );
    }

    private void assertRejected(
        StagingSyntheticBootstrapExecutionContext context,
        String errorCode
    ) {
        assertThatThrownBy(() -> guard.validate(context, validSpec()))
            .isInstanceOfSatisfying(
                StagingSyntheticBootstrapException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                    .isEqualTo(errorCode)
            );
    }

    private StagingSyntheticBootstrapExecutionContext validContext() {
        return context(null, null, null, null, null);
    }

    private StagingSyntheticBootstrapExecutionContext context(
        Set<String> profiles,
        String project,
        String root,
        String printingMode,
        Boolean printingEnabled
    ) {
        return new StagingSyntheticBootstrapExecutionContext(
            profiles == null ? Set.of("cloud", "staging-synthetic-bootstrap") : profiles,
            project == null ? "restaurant-pos-staging" : project,
            root == null ? "/srv/restaurant-pos/staging" : root,
            RUNTIME_SHA,
            RUNTIME_SHA,
            TOOL_SHA,
            printingMode == null ? "DISABLED" : printingMode,
            printingEnabled != null && printingEnabled,
            "jdbc:postgresql://db:5432/restaurant_pos_staging",
            "restaurant_pos_staging",
            "none"
        );
    }

    private StagingSyntheticBootstrapSpec validSpec() {
        return new StagingSyntheticBootstrapSpec(
            "STG005_20260730_R01",
            "STG005_ORG_20260730_R01",
            "STG005_ORG_20260730_R01",
            "STG005_SRC_20260730_R01",
            "STG005_SRC_20260730_R01",
            "STG005_OWNER_20260730_R01",
            "STG005_OWNER_20260730_R01",
            RUNTIME_SHA,
            TOOL_SHA
        );
    }
}
