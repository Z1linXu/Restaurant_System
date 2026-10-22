package com.restaurant.system.integration.ubereats;

import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import com.restaurant.system.integration.ubereats.service.UberEatsProcessingGate;
import com.restaurant.system.integration.ubereats.service.UberEatsException;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.*;

class UberEatsProcessingGateTest {
    private static final String STORE_A = "00000000-0000-4000-8000-000000000001";
    private static final String STORE_B = "00000000-0000-4000-8000-000000000002";
    private final UberEatsProperties config = new UberEatsProperties();
    private final UberEatsProcessingGate gate = new UberEatsProcessingGate(config);

    @Test
    void productionWorkerRequiresExplicitUtcCutoverButReceiveOnlyDoesNot() {
        config.enabled = true;
        config.environment = "production";
        assertThatThrownBy(gate::validate).isInstanceOf(IllegalStateException.class);
        config.workerEnabled = false;
        assertThatCode(gate::validate).doesNotThrowAnyException();
        assertThatThrownBy(gate::requireProcessing).isInstanceOf(UberEatsException.class);
        gate.notBefore = "2026-10-21T20:00:00Z";
        config.workerEnabled = true;
        assertThatThrownBy(gate::requireProcessing).hasMessageContaining("Store UTC cutovers");
        gate.storeCutovers = "{\"" + STORE_A + "\":\"2026-10-21T20:00:00Z\"}";
        assertThatCode(gate::requireProcessing).doesNotThrowAnyException();
    }

    @Test
    void invalidCutoverIsRejectedWithoutBreakingDisabledOptionalIntegration() {
        config.enabled = true;
        for (String invalid : new String[]{"not-a-date", "2026-10-21T20:00:00", "2026-10-21T16:00:00-04:00"}) {
            gate.notBefore = invalid;
            assertThatThrownBy(gate::validate).isInstanceOf(IllegalStateException.class);
        }
        config.enabled = false;
        assertThatCode(gate::validate).doesNotThrowAnyException();
    }

    @Test
    void delayedOldOrdersAndMissingPlacementAreNotAuthorizedByFreshReceipts() {
        gate.notBefore = "2026-10-21T20:00:00Z";
        assertThat(gate.permitsPlaced("2026-10-21T19:59:59Z")).isFalse();
        assertThat(gate.permitsPlaced("2026-10-21T16:00:00-04:00")).isTrue();
        assertThat(gate.permitsPlaced("")).isFalse();
        assertThat(gate.permitsPlaced(null)).isFalse();
        assertThat(gate.permitsPlaced("invalid")).isFalse();
    }

    @Test
    void receiptUsesJvmLocalClockAndFrozenOrderPlacementUsesUtc() {
        Instant start = Instant.parse("2026-10-21T20:00:00Z");
        gate.notBefore = start.toString();
        LocalDateTime local = LocalDateTime.ofInstant(start, ZoneId.systemDefault());
        assertThat(gate.permitsReceipt(local.minusSeconds(1))).isFalse();
        assertThat(gate.permitsReceipt(local)).isTrue();
        var row = new UberEatsOrder();
        row.createdAt = local;
        row.placedAt = LocalDateTime.ofInstant(start, ZoneOffset.UTC);
        assertThat(gate.permitsOrder(row)).isTrue();
        row.placedAt = row.placedAt.minusSeconds(1);
        assertThat(gate.permitsOrder(row)).isFalse();
        row.localOrderId = 1L;
        assertThat(gate.permitsOrder(row)).isTrue(); // Existing kitchen history is never replayed.
    }

    @Test
    void unchangedSandboxHasNoImplicitCutoverAndDisabledProcessingFailsClosed() {
        assertThat(gate.permitsPlaced("")).isTrue();
        assertThat(gate.permitsReceipt(null)).isTrue();
        config.enabled = true;
        assertThatCode(gate::requireProcessing).doesNotThrowAnyException();
        config.enabled = false;
        assertThatThrownBy(gate::requireProcessing).isInstanceOf(UberEatsException.class);
    }

    @Test
    void productionWorkerRejectsEmptyStoreMapAndInvalidEntriesWithoutLeakingValues() {
        config.enabled = true;
        config.environment = "production";
        gate.notBefore = "2026-10-21T20:00:00Z";
        for (String empty : new String[]{"", "{}"}) {
            gate.storeCutovers = empty;
            assertThatThrownBy(gate::validate).hasMessageContaining("Store UTC cutovers");
        }
        for (String invalid : new String[]{
            "[]", "null", "broken-private-fixture", "{} {}",
            "{\"not-a-uuid\":\"2026-10-21T20:00:00Z\"}",
            "{\"" + STORE_A + "\":null}",
            "{\"" + STORE_A + "\":42}",
            "{\"" + STORE_A + "\":\"2026-10-21T20:00:00\"}",
            "{\"" + STORE_A + "\":\"2026-10-21T16:00:00-04:00\"}",
            "{\"" + STORE_A + "\":\"2026-10-21T20:00:00Z\",\"" + STORE_A + "\":\"2026-10-21T21:00:00Z\"}"
        }) {
            gate.storeCutovers = invalid;
            assertThatThrownBy(gate::validate)
                    .hasMessage("UBER_EATS_STORE_CUTOVERS must be a JSON object of unique Store UUIDs to ISO-8601 UTC instants")
                    .hasNoCause();
        }
        config.enabled = false;
        assertThatCode(gate::validate).doesNotThrowAnyException();
    }

    @Test
    void onlyExplicitlyActivatedStoreCanImportEvenAfterGlobalCutover() {
        productionStores("2026-10-21T21:00:00Z", null);
        LocalDateTime received = local("2026-10-21T22:00:00Z");
        assertThat(gate.permitsStore(STORE_A)).isTrue();
        assertThat(gate.permitsStore(STORE_B)).isFalse();
        assertThat(gate.permitsReceipt(STORE_A, received)).isTrue();
        assertThat(gate.permitsReceipt(STORE_B, received)).isFalse();
        assertThat(gate.permitsPlaced(STORE_A, "2026-10-21T22:00:00Z")).isTrue();
        assertThat(gate.permitsPlaced(STORE_B, "2026-10-21T22:00:00Z")).isFalse();
        assertThat(gate.permitsReceipt(received)).isFalse();
        assertThat(gate.permitsPlaced("2026-10-21T22:00:00Z")).isFalse();
    }

    @Test
    void storeBoundaryUsesLaterOfGlobalAndIndividualCutover() {
        productionStores("2026-10-21T19:00:00Z", "2026-10-21T21:00:00Z");
        assertThat(gate.permitsPlaced(STORE_A, "2026-10-21T19:59:59Z")).isFalse();
        assertThat(gate.permitsPlaced(STORE_A, "2026-10-21T20:00:00Z")).isTrue();
        assertThat(gate.permitsPlaced(STORE_B, "2026-10-21T20:59:59Z")).isFalse();
        assertThat(gate.permitsPlaced(STORE_B, "2026-10-21T21:00:00Z")).isTrue();
    }

    @Test
    void advancingPausedStoreCutoverLeavesOtherStoreUnchanged() {
        productionStores("2026-10-21T20:00:00Z", "2026-10-21T20:00:00Z");
        assertThat(gate.permitsPlaced(STORE_A, "2026-10-21T20:30:00Z")).isTrue();
        assertThat(gate.permitsPlaced(STORE_B, "2026-10-21T20:30:00Z")).isTrue();
        productionStores("2026-10-21T21:00:00Z", "2026-10-21T20:00:00Z");
        assertThat(gate.permitsPlaced(STORE_A, "2026-10-21T20:30:00Z")).isFalse();
        assertThat(gate.permitsReceipt(STORE_A, local("2026-10-21T20:30:00Z"))).isFalse();
        assertThat(gate.permitsPlaced(STORE_B, "2026-10-21T20:30:00Z")).isTrue();
        assertThat(gate.permitsReceipt(STORE_B, local("2026-10-21T20:30:00Z"))).isTrue();
    }

    @Test
    void freshReceiptCannotAuthorizeOldOrMissingPlacementForActivatedStore() {
        productionStores("2026-10-21T21:00:00Z", null);
        assertThat(gate.permitsReceipt(STORE_A, local("2026-10-21T22:00:00Z"))).isTrue();
        assertThat(gate.permitsPlaced(STORE_A, "2026-10-21T20:59:59Z")).isFalse();
        assertThat(gate.permitsPlaced(STORE_A, "")).isFalse();
        var row = new UberEatsOrder();
        row.uberStoreId = STORE_A;
        row.createdAt = local("2026-10-21T22:00:00Z");
        row.placedAt = LocalDateTime.parse("2026-10-21T20:59:59");
        assertThat(gate.permitsOrder(row)).isFalse();
        row.placedAt = LocalDateTime.parse("2026-10-21T21:00:00");
        assertThat(gate.permitsOrder(row)).isTrue();
    }

    @Test
    void alreadyImportedHistoryCanReceiveSafetyUpdatesAfterStoreRemoval() {
        productionStores("2026-10-21T21:00:00Z", null);
        var row = new UberEatsOrder();
        row.uberStoreId = STORE_B;
        assertThat(gate.permitsOrder(row)).isFalse();
        row.localOrderId = 17L;
        assertThat(gate.permitsOrder(row)).isTrue();
    }

    @Test
    void optionalSandboxStoreMapHasSameRestrictionWithoutChangingEmptyMapDefault() {
        gate.storeCutovers = "{\"" + STORE_A + "\":\"2026-10-21T20:00:00Z\"}";
        assertThat(gate.permitsPlaced(STORE_A, "2026-10-21T20:00:00Z")).isTrue();
        assertThat(gate.permitsPlaced(STORE_B, "2026-10-21T20:00:00Z")).isFalse();
        gate.storeCutovers = "{}";
        assertThat(gate.permitsPlaced(STORE_B, "")).isTrue();
    }

    private void productionStores(String first, String second) {
        config.enabled = true;
        config.environment = "production";
        gate.notBefore = "2026-10-21T20:00:00Z";
        gate.storeCutovers = "{\"" + STORE_A + "\":\"" + first + "\""
                + (second == null ? "" : ",\"" + STORE_B + "\":\"" + second + "\"") + "}";
        gate.validate();
    }

    private LocalDateTime local(String utc) {
        return LocalDateTime.ofInstant(Instant.parse(utc), ZoneId.systemDefault());
    }
}
