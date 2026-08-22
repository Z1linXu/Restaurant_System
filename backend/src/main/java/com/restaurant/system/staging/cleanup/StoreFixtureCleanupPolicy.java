package com.restaurant.system.staging.cleanup;

import com.restaurant.system.user.entity.Store;
import java.util.Set;

final class StoreFixtureCleanupPolicy {

    static final long PROTECTED_SOURCE_STORE_ID = 1L;
    static final Set<Long> APPROVED_OWNER_MANUAL_STORE_IDS = Set.of(9L, 12L);

    private StoreFixtureCleanupPolicy() {
    }

    static Classification classify(Store store, Set<Long> explicitlyApprovedOwnerManualIds) {
        if (store == null || store.id == null) {
            return Classification.unknown("Store is missing");
        }
        if (store.id == PROTECTED_SOURCE_STORE_ID) {
            return Classification.protectedStore("STG005/source/reference Store is immutable in this path");
        }
        if (explicitlyApprovedOwnerManualIds.contains(store.id)) {
            if (APPROVED_OWNER_MANUAL_STORE_IDS.contains(store.id)
                && isPhaseBFixture(store)
                && !hasMachineFixtureCode(store)) {
                return new Classification("DELETE_OWNER_MANUAL_TEST", "Owner-approved manual Staging test fixture", true);
            }
            return Classification.unknown("Owner-manual approval does not match the audited fixture identity");
        }
        if (isA10Fixture(store)) {
            return new Classification("DELETE_AUTOMATED_TEST", "A10 validation fixture with legacy validation provenance", true);
        }
        if (isPhaseBFixture(store) && hasMachineFixtureCode(store)) {
            return new Classification("DELETE_AUTOMATED_TEST", "Phase B synthetic validation fixture", true);
        }
        return Classification.unknown("Store is not an approved synthetic/validation fixture");
    }

    private static boolean isA10Fixture(Store store) {
        return "BUSINESS".equalsIgnoreCase(store.store_kind)
            && "LEGACY_EXISTING_STORE".equalsIgnoreCase(store.provisioning_source)
            && "A10_VALIDATION_INACTIVE".equalsIgnoreCase(store.status)
            && store.code != null
            && store.code.startsWith("A10_VALIDATION_STORE_");
    }

    private static boolean isPhaseBFixture(Store store) {
        return "VALIDATION_FIXTURE".equalsIgnoreCase(store.store_kind)
            && "PHASE_B_OWNER_PROVISIONING".equalsIgnoreCase(store.provisioning_source);
    }

    private static boolean hasMachineFixtureCode(Store store) {
        return store.code != null && store.code.startsWith("PHASE_B_VALIDATION_STORE_");
    }

    record Classification(String code, String reason, boolean deletable) {
        static Classification protectedStore(String reason) {
            return new Classification("KEEP_STG005", reason, false);
        }

        static Classification unknown(String reason) {
            return new Classification("REVIEW_UNSAFE_OR_UNKNOWN", reason, false);
        }
    }
}
