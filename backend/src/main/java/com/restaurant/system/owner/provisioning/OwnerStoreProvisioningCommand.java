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
    String masterMenuFingerprintSha256
) {
}
