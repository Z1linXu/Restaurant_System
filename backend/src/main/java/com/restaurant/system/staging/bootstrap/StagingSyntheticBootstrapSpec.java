package com.restaurant.system.staging.bootstrap;

public record StagingSyntheticBootstrapSpec(
    String runId,
    String organizationName,
    String organizationCode,
    String sourceStoreName,
    String sourceStoreCode,
    String ownerLoginIdentifier,
    String ownerFullName,
    String runtimeSha,
    String toolSha
) {
}
