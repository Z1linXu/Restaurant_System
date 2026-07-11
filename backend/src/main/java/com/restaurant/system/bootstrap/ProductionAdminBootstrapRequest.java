package com.restaurant.system.bootstrap;

public record ProductionAdminBootstrapRequest(
    String organizationName,
    String storeName,
    String ownerUsername,
    String ownerFullName,
    String ownerContact,
    String ownerPassword,
    boolean dryRun
) {
}
