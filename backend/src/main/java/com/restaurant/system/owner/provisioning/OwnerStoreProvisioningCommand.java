package com.restaurant.system.owner.provisioning;

import com.restaurant.system.common.auth.AuthenticatedUser;

public record OwnerStoreProvisioningCommand(
    AuthenticatedUser actor,
    Long organizationId,
    String idempotencyKey,
    String storeName,
    String storeCode,
    String profileCode,
    String profileVersion,
    String profileFingerprintSha256,
    String masterMenuKey,
    String masterMenuVersion,
    String masterMenuFingerprintSha256,
    StoreProvisioningPurpose purpose
) {
    public OwnerStoreProvisioningCommand(
        AuthenticatedUser actor,
        Long organizationId,
        String idempotencyKey,
        String storeName,
        String storeCode,
        String profileCode,
        String profileVersion,
        String profileFingerprintSha256,
        String masterMenuKey,
        String masterMenuVersion,
        String masterMenuFingerprintSha256
    ) {
        this(
            actor,
            organizationId,
            idempotencyKey,
            storeName,
            storeCode,
            profileCode,
            profileVersion,
            profileFingerprintSha256,
            masterMenuKey,
            masterMenuVersion,
            masterMenuFingerprintSha256,
            StoreProvisioningPurpose.STAGING_VALIDATION
        );
    }

    public boolean isBusinessCreation() {
        return purpose == StoreProvisioningPurpose.BUSINESS;
    }
}
