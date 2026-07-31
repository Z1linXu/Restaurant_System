package com.restaurant.system.staging.bootstrap;

public record StagingSyntheticBootstrapResult(
    Long bootstrapRequestId,
    String runId,
    Long organizationId,
    Long sourceStoreId,
    Long ownerUserId,
    String runtimeSha,
    String toolSha,
    String resultCode,
    boolean replayed
) {
}
