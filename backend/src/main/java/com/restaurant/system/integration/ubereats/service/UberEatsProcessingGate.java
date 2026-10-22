package com.restaurant.system.integration.ubereats.service;

import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;

/** Operational UTC boundary. Held receipts are never automatically promoted by moving it. */
@Component
public class UberEatsProcessingGate {
    private final UberEatsProperties config;

    @Value("${UBER_EATS_PROCESSING_NOT_BEFORE:}")
    public String notBefore = "";

    public UberEatsProcessingGate(UberEatsProperties config) {
        this.config = config;
    }

    @PostConstruct
    public void validate() {
        if (!config.enabled) return;
        cutoff();
        if (config.workerEnabled && "production".equals(config.environment) && notBefore.isBlank())
            throw new IllegalStateException("Production Uber processing requires an explicit UTC cutover");
    }

    private Instant cutoff() {
        if (notBefore.isBlank()) return null;
        try {
            if (!notBefore.endsWith("Z")) throw new IllegalArgumentException();
            return Instant.parse(notBefore);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("UBER_EATS_PROCESSING_NOT_BEFORE must be an ISO-8601 UTC instant");
        }
    }

    public void requireProcessing() {
        config.requireProcessingEnabled();
        validate();
    }

    public boolean permitsReceipt(LocalDateTime receivedAt) {
        Instant start = cutoff();
        // Existing event timestamps use the JVM-local clock; do not compare them as UTC.
        return start == null || receivedAt != null
                && !receivedAt.atZone(ZoneId.systemDefault()).toInstant().isBefore(start);
    }

    public boolean permitsPlaced(String placedAt) {
        Instant start = cutoff();
        if (start == null) return true;
        try {
            return !OffsetDateTime.parse(placedAt).toInstant().isBefore(start);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean permitsOrder(UberEatsOrder row) {
        if (row.localOrderId != null || cutoff() == null) return true;
        return permitsReceipt(row.createdAt) && row.placedAt != null
                && !row.placedAt.toInstant(ZoneOffset.UTC).isBefore(cutoff());
    }
}
