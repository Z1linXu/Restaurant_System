package com.restaurant.system.integration.ubereats;

import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import com.restaurant.system.integration.ubereats.service.UberEatsProcessingGate;
import com.restaurant.system.integration.ubereats.service.UberEatsException;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.*;

class UberEatsProcessingGateTest {
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
}
