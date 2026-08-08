package com.restaurant.system.owner.profile;

/**
 * Versioned, non-secret desired-state declaration for Store provisioning.
 */
public interface StoreProfileDescriptor {

    String profileCode();

    String profileVersion();

    StoreProfileComposition composition();

    default String profileFingerprint() {
        return StoreProfileFingerprint.compute(this);
    }
}
