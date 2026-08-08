package com.restaurant.system.staging.menu;

public record StagingSyntheticSourceMenuResult(
    Long bootstrapRequestId,
    Long sourceStoreId,
    String runtimeSha,
    String toolSha,
    String manifestCode,
    String manifestVersion,
    String manifestFingerprint,
    Long revisionBefore,
    Long revisionAfter,
    int categoryCount,
    int stationCount,
    int itemCount,
    int optionCount,
    String resultCode,
    boolean replayed
) {
}
