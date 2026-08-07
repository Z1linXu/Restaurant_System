package com.restaurant.system.owner.service;

/** Internal, read-only validation input. No HTTP endpoint is introduced in PR-F0. */
public record OwnerStoreMenuCloneValidationCommand(
    Long organizationId,
    Long sourceStoreId,
    Long targetStoreId,
    String profileCode
) {
}
