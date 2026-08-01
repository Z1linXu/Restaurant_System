package com.restaurant.system.owner.menu;

/**
 * Reviewed, versioned identity contract for a Store menu clone profile.
 */
public interface StoreMenuCloneProfileDescriptor {

    String profileCode();

    Long sourceStoreId();

    String profileFingerprint();
}
