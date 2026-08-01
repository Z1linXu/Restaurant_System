package com.restaurant.system.owner.service;

public record OwnerStoreMenuCloneTransactionCommand(
    Long requestId,
    Long organizationId,
    Long sourceStoreId,
    Long targetStoreId,
    String profileCode,
    Long actorUserId
) {
}
