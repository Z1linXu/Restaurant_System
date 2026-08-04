package com.restaurant.system.owner.menu;

import java.util.Objects;

public record StoreMenuCloneCompositionContext(
    StoreMenuCloneBaseGraphProfile profile,
    Long sourceStoreId,
    Long targetStoreId,
    StoreMenuCloneBaseGraphResult baseGraph
) {

    public StoreMenuCloneCompositionContext {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sourceStoreId, "sourceStoreId");
        Objects.requireNonNull(targetStoreId, "targetStoreId");
        Objects.requireNonNull(baseGraph, "baseGraph");
    }
}
