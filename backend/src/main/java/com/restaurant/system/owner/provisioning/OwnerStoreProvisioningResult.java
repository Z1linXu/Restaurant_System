package com.restaurant.system.owner.provisioning;

public record OwnerStoreProvisioningResult(
    Long requestId,
    Long storeId,
    String status,
    boolean replayed,
    String validationStatus,
    String resultCode,
    String errorCode,
    OwnerStoreProvisioningCounts counts
) {
}
