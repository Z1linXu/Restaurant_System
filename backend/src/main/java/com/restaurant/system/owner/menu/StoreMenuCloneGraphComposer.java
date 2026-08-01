package com.restaurant.system.owner.menu;

/** Internal extension point shared by read-only planning and locked clone execution. */
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
