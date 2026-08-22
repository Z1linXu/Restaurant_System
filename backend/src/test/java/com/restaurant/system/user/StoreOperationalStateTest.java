package com.restaurant.system.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.system.user.entity.Store;
import org.junit.jupiter.api.Test;

class StoreOperationalStateTest {

    @Test
    void liveRequiresBothCanonicalFieldsAndIgnoresStoreKind() {
        Store store = new Store();
        store.status = "active";
        store.lifecycle_status = "ACTIVE";
        store.store_kind = "VALIDATION_FIXTURE";

        assertThat(StoreOperationalState.isLive(store)).isTrue();
        assertThat(StoreOperationalState.value(store)).isEqualTo("LIVE");

        store.lifecycle_status = "READY_FOR_REVIEW";
        assertThat(StoreOperationalState.isLive(store)).isFalse();
        assertThat(StoreOperationalState.value(store)).isEqualTo("NOT_LIVE");
    }
}
