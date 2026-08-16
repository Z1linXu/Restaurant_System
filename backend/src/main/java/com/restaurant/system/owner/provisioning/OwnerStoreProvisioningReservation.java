package com.restaurant.system.owner.provisioning;

public record OwnerStoreProvisioningReservation(
    Long requestId,
    Long organizationId,
    Long storeId,
    String storeName,
    String storeCode,
    String profileCode,
    String profileVersion,
    String profileFingerprintSha256,
    String masterMenuKey,
    String masterMenuVersion,
    String masterMenuFingerprintSha256,
    String status,
    boolean replayed,
    String validationStatus,
    String resultCode,
    String errorCode,
    OwnerStoreProvisioningCounts counts
) {
}
