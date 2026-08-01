package com.restaurant.system.owner.service;

public record OwnerStoreMenuCloneSuccessEvidence(
    Long requestId,
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
