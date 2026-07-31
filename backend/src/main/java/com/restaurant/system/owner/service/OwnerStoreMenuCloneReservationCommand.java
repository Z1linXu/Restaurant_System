package com.restaurant.system.owner.service;

public record OwnerStoreMenuCloneReservationCommand(
    Long organizationId,
    Long sourceStoreId,
    Long targetStoreId,
    String idempotencyKey,
    String profileCode,
    Long actorUserId
) {

    @Override
    public String toString() {
        return "OwnerStoreMenuCloneReservationCommand[redacted]";
    }
}
