package com.restaurant.system.integration.ubereats.service;

import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;

import java.time.LocalDateTime;
import java.util.Set;

/** Bounded durable polling policy. Scheduling/leases use existing JVM-local nextAttemptAt. */
public final class UberEatsAcceptancePolling {
    public static final int MAX_ATTEMPTS = 40;
    public static final int MAX_MINUTES = 30;
    private static final int[] DELAYS = {2, 5, 10, 15, 30, 60};

    private UberEatsAcceptancePolling() {}

    public static boolean waiting(UberEatsOrder row) {
        return "KITCHEN_MIRROR".equals(row.processingMode)
                && Set.of("WAITING_FOR_ACCEPTANCE", "WAITING_FOR_RELEASE").contains(row.status);
    }

    public static int delaySeconds(int attempts) {
        return attempts < DELAYS.length ? DELAYS[Math.max(0, attempts)] : 120;
    }

    public static boolean expired(UberEatsOrder row, LocalDateTime utcNow) {
        return row.acceptancePollCount >= MAX_ATTEMPTS
                || (row.acceptancePollExpiresAt != null
                        && !utcNow.isBefore(row.acceptancePollExpiresAt));
    }
}
