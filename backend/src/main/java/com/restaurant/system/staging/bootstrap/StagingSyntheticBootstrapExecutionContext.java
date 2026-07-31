package com.restaurant.system.staging.bootstrap;

import java.util.Set;

public record StagingSyntheticBootstrapExecutionContext(
    Set<String> activeProfiles,
    String composeProject,
    String stagingRoot,
    String expectedRuntimeSha,
    String observedRuntimeSha,
    String toolSha,
    String printingMode,
    boolean printingFeatureEnabled,
    String datasourceUrl,
    String datasourceUsername,
    String webApplicationType
) {
}
