package com.restaurant.system.owner.service;

public record OwnerStoreMenuCloneValidationCommand(
    Long organizationId,
    Long sourceStoreId,
    Long targetStoreId,
    String profileCode
) {
}
