package com.restaurant.system.integration.ubereats.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;

/** Operational UTC boundaries. Held receipts are never automatically promoted by moving them. */
@Component
public class UberEatsProcessingGate {
    private static final ObjectMapper JSON =
            new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final UberEatsProperties config;

    @Value("${UBER_EATS_PROCESSING_NOT_BEFORE:}")
    public String notBefore = "";

    @Value("${UBER_EATS_STORE_CUTOVERS:}")
    public String storeCutovers = "";

    public UberEatsProcessingGate(UberEatsProperties config) {
        this.config = config;
    }

    @PostConstruct
    public void validate() {
        if (!config.enabled) return;
        cutoff();
        var stores = configuredStores();
        if (config.workerEnabled && "production".equals(config.environment)) {
            if (notBefore.isBlank())
                throw new IllegalStateException("Production Uber processing requires an explicit UTC cutover");
            if (stores.isEmpty())
                throw new IllegalStateException("Production Uber processing requires explicit Store UTC cutovers");
        }
    }

    private Instant cutoff() {
        if (notBefore.isBlank()) return null;
        try {
            return utcInstant(notBefore);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("UBER_EATS_PROCESSING_NOT_BEFORE must be an ISO-8601 UTC instant");
        }
    }

    private Map<String, Instant> configuredStores() {
        if (storeCutovers.isBlank()) return Map.of();
        try {
            var object = JSON.readTree(storeCutovers);
            if (object == null || !object.isObject()) throw new IllegalArgumentException();
            Map<String, Instant> result = new HashMap<>();
            var entries = object.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                String store = UUID.fromString(entry.getKey()).toString();
                if (!store.equalsIgnoreCase(entry.getKey()) || !entry.getValue().isTextual())
                    throw new IllegalArgumentException();
                if (result.putIfAbsent(store, utcInstant(entry.getValue().asText())) != null)
                    throw new IllegalArgumentException();
            }
            return result;
        } catch (Exception ex) {
            // Never include raw configuration or parser diagnostics in a startup error.
            throw new IllegalStateException("UBER_EATS_STORE_CUTOVERS must be a JSON object of unique Store UUIDs to ISO-8601 UTC instants");
        }
    }

    private static Instant utcInstant(String value) {
        if (!value.endsWith("Z")) throw new IllegalArgumentException();
        return Instant.parse(value);
    }

    private record Boundary(boolean permitted, Instant start) {}

    private Boundary boundary(String uberStoreId) {
        Instant global = cutoff();
        Map<String, Instant> stores = configuredStores();
        if ("production".equals(config.environment) || !stores.isEmpty()) {
            Instant store = uberStoreId == null ? null : stores.get(uberStoreId);
            if (store == null) return new Boundary(false, null);
            return new Boundary(true, global == null || store.isAfter(global) ? store : global);
        }
        return new Boundary(true, global);
    }

    public void requireProcessing() {
        config.requireProcessingEnabled();
        validate();
    }

    /** Store activation ceiling only; cancellation/edit safety does not expire by order age. */
    public boolean permitsStore(String uberStoreId) {
        return boundary(uberStoreId).permitted();
    }

    public boolean permitsReceipt(LocalDateTime receivedAt) {
        return permitsReceipt(null, receivedAt);
    }

    public boolean permitsReceipt(String uberStoreId, LocalDateTime receivedAt) {
        var boundary = boundary(uberStoreId);
        // Existing event timestamps use the JVM-local clock; do not compare them as UTC.
        return boundary.permitted() && (boundary.start() == null || receivedAt != null
                && !receivedAt.atZone(ZoneId.systemDefault()).toInstant().isBefore(boundary.start()));
    }

    public boolean permitsPlaced(String placedAt) {
        return permitsPlaced(null, placedAt);
    }

    public boolean permitsPlaced(String uberStoreId, String placedAt) {
        var boundary = boundary(uberStoreId);
        if (!boundary.permitted()) return false;
        if (boundary.start() == null) return true;
        try {
            return !OffsetDateTime.parse(placedAt).toInstant().isBefore(boundary.start());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean permitsOrder(UberEatsOrder row) {
        if (row.localOrderId != null) return true; // Keep historical cancellation/edit safety live.
        var boundary = boundary(row.uberStoreId);
        if (!boundary.permitted()) return false;
        if (boundary.start() == null) return true;
        return permitsReceipt(row.uberStoreId, row.createdAt) && row.placedAt != null
                && !row.placedAt.toInstant(ZoneOffset.UTC).isBefore(boundary.start());
    }
}
