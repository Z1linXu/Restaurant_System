package com.restaurant.system.user;

import com.restaurant.system.user.entity.Store;

/** Canonical Store operational lifecycle contract. */
public final class StoreOperationalState {

    public static final String LIVE = "LIVE";
    public static final String NOT_LIVE = "NOT_LIVE";

    private StoreOperationalState() {
    }

    public static boolean isLive(Store store) {
        return store != null
            && "active".equalsIgnoreCase(store.status)
            && "ACTIVE".equalsIgnoreCase(store.lifecycle_status);
    }

    public static String value(Store store) {
        return isLive(store) ? LIVE : NOT_LIVE;
    }
}
