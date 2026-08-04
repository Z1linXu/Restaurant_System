package com.restaurant.system.owner.menu;

/**
 * Internal extension point invoked inside the clone transaction while both Store locks are held.
 */
public interface StoreMenuCloneGraphComposer {

    String identity();

    Phase phase();

    int order();

    boolean supports(String profileCode);

    int compose(StoreMenuCloneCompositionContext context);

    enum Phase {
        SOURCE_OPTIONS,
        PROFILE_OVERRIDES
    }
}
