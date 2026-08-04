package com.restaurant.system.owner.service;

public record OwnerStoreMenuCloneSuccessEvidence(
    Long requestId,
    Long organizationId,
    Long sourceStoreId,
    Long targetStoreId,
    String profileCode,
    Long sourceMenuRevision,
    Long targetRevisionBefore,
    Long targetRevisionAfter,
    int createdStationCount,
    int createdCategoryCount,
    int createdItemCount,
    int createdOptionCount,
    String resultCode
) {
}
