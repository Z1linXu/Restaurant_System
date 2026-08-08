package com.restaurant.system.owner.profile;

/**
 * Reviewed module/configuration reference. The expected fingerprint is bound
 * into the parent profile; a future module registry verifies its authenticity.
 */
public record StoreProfileModuleReference(
    StoreProvisioningModuleCode moduleCode,
    String contractVersion,
    StoreProfileModulePolicy policy,
    String configurationReference,
    String expectedConfigurationFingerprint
) {
}
