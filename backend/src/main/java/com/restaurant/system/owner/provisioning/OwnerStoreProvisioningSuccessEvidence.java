package com.restaurant.system.owner.provisioning;

public record OwnerStoreProvisioningSuccessEvidence(
    Long requestId,
    Long organizationId,
    Long storeId,
    String profileCode,
    String profileVersion,
    String profileFingerprintSha256,
    String masterMenuKey,
    String masterMenuVersion,
    String masterMenuFingerprintSha256,
    String validationStatus,
    OwnerStoreProvisioningCounts counts,
    String resultCode
) {
}
