package com.restaurant.system.owner.service;

public record OwnerStoreMenuCloneReservation(
    Long requestId,
    Long organizationId,
    Long sourceStoreId,
    Long targetStoreId,
    String profileCode,
    String status,
    boolean replayed,
    Long sourceMenuRevision,
    Long targetRevisionBefore,
    Long targetRevisionAfter,
    Integer createdStationCount,
    Integer createdCategoryCount,
    Integer createdItemCount,
    Integer createdOptionCount,
    String resultCode,
    String errorCode
) {
}
