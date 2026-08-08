package com.restaurant.system.staging.menu;

public record StagingSyntheticSourceMenuSpec(
    Long sourceStoreId,
    String sourceStoreCode,
    String runtimeSha,
    String toolSha
) {
}
