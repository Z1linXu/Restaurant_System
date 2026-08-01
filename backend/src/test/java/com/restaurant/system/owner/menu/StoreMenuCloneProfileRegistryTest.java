package com.restaurant.system.owner.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class StoreMenuCloneProfileRegistryTest {

    @Test
    void resolvesReviewedProfilesWithoutSharedStoreSpecificBranches() {
        StoreMenuCloneProfileDescriptor profile = profile("GENERIC_PROFILE_V1", 7L, "fingerprint-v1");
        StoreMenuCloneProfileRegistry registry = new StoreMenuCloneProfileRegistry(List.of(profile));

        assertThat(registry.find(" generic_profile_v1 ")).containsSame(profile);
        assertThat(registry.find("UNKNOWN_PROFILE")).isEmpty();
    }

    @Test
    void rejectsDuplicateOrIncompleteDescriptors() {
        StoreMenuCloneProfileDescriptor first = profile("DUPLICATE", 1L, "fingerprint-a");
        StoreMenuCloneProfileDescriptor second = profile("duplicate", 2L, "fingerprint-b");

        assertThatThrownBy(() -> new StoreMenuCloneProfileRegistry(List.of(first, second)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new StoreMenuCloneProfileRegistry(List.of(profile(" ", 1L, "value"))))
            .isInstanceOf(IllegalStateException.class);
    }

    private StoreMenuCloneProfileDescriptor profile(String code, Long sourceStoreId, String fingerprint) {
        return new StoreMenuCloneProfileDescriptor() {
            @Override
            public String profileCode() {
                return code;
            }

            @Override
            public Long sourceStoreId() {
                return sourceStoreId;
            }

            @Override
            public String profileFingerprint() {
                return fingerprint;
            }
        };
    }
}
