package com.restaurant.system.staging.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.system.user.entity.Store;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoreFixtureCleanupPolicyTest {

    @Test
    void protectsAuditedSourceStore() {
        Store store = store(1L, "STG005_SRC_20260809_R01", "BUSINESS", "LEGACY_EXISTING_STORE", "active");

        StoreFixtureCleanupPolicy.Classification classification = StoreFixtureCleanupPolicy.classify(store, Set.of());

        assertEquals("KEEP_STG005", classification.code());
        assertFalse(classification.deletable());
    }

    @Test
    void acceptsA10OnlyWithStatusAndProvenanceMarkers() {
        Store store = store(2L, "A10_VALIDATION_STORE_20260815_015431", "BUSINESS", "LEGACY_EXISTING_STORE", "A10_VALIDATION_INACTIVE");

        StoreFixtureCleanupPolicy.Classification classification = StoreFixtureCleanupPolicy.classify(store, Set.of());

        assertEquals("DELETE_AUTOMATED_TEST", classification.code());
        assertTrue(classification.deletable());
    }

    @Test
    void rejectsNameOnlyOrUnknownValidationStore() {
        Store store = store(18L, "PHASE_B_VALIDATION_STORE_LOOKALIKE", "BUSINESS", "LEGACY_EXISTING_STORE", "inactive");

        StoreFixtureCleanupPolicy.Classification classification = StoreFixtureCleanupPolicy.classify(store, Set.of());

        assertEquals("REVIEW_UNSAFE_OR_UNKNOWN", classification.code());
        assertFalse(classification.deletable());
    }

    @Test
    void manualStoreRequiresExplicitAuditedApproval() {
        Store store = store(9L, "CHINATOWN", "VALIDATION_FIXTURE", "PHASE_B_OWNER_PROVISIONING", "inactive");

        assertFalse(StoreFixtureCleanupPolicy.classify(store, Set.of()).deletable());
        assertEquals("DELETE_OWNER_MANUAL_TEST", StoreFixtureCleanupPolicy.classify(store, Set.of(9L)).code());
    }

    @Test
    void manualApprovalDoesNotTurnAnUnrelatedStoreIntoFixture() {
        Store store = store(9L, "CHINATOWN", "BUSINESS", "LEGACY_EXISTING_STORE", "active");

        StoreFixtureCleanupPolicy.Classification classification = StoreFixtureCleanupPolicy.classify(store, Set.of(9L));

        assertEquals("REVIEW_UNSAFE_OR_UNKNOWN", classification.code());
        assertFalse(classification.deletable());
    }

    private static Store store(Long id, String code, String kind, String source, String status) {
        Store store = new Store();
        store.id = id;
        store.code = code;
        store.store_kind = kind;
        store.provisioning_source = source;
        store.status = status;
        return store;
    }
}
