package com.restaurant.system.bootstrap;

public record ProductionAdminBootstrapResult(
    String organizationName,
    String storeName,
    String username,
    boolean dryRun
) {
}
