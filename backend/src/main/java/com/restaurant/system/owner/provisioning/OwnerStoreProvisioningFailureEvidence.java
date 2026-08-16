package com.restaurant.system.owner.provisioning;

public record OwnerStoreProvisioningFailureEvidence(
    Long requestId,
    Long storeId,
    String errorCode
) {
}
