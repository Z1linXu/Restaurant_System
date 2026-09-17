package com.restaurant.system.integration.ubereats.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.repository.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class UberEatsWebhookService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private static final Set<String> EVENTS =
            Set.of(
                    "orders.notification",
                    "orders.cancel",
                    "orders.scheduled.notification",
                    "orders.customer_order_edit");
    private final UberEatsProperties config;
    private final UberEatsInboxEvents inboxEvents;
    private final ObjectMapper json;
    private final UberEatsEventRepository events;
    private final UberEatsStoreMappingRepository stores;
    private final UberEatsOrderRepository orders;

    public UberEatsWebhookService(
            UberEatsProperties config,
            ObjectMapper json,
            UberEatsEventRepository events,
            UberEatsStoreMappingRepository stores,
            UberEatsOrderRepository orders,
            UberEatsInboxEvents inboxEvents) {
        this.inboxEvents = inboxEvents;
        this.config = config;
        this.json = json;
        this.events = events;
        this.stores = stores;
        this.orders = orders;
    }

    public boolean validSignature(byte[] raw, String signature) {
        if (config.clientSecret().isBlank()
                || signature == null
                || !signature.matches("[0-9a-f]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            config.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(raw), HexFormat.of().parseHex(signature));
        } catch (Exception ex) {
            return false;
        }
    }

    @Transactional
    public void receive(byte[] raw, String signature, String environment) {
        if (!config.enabled)
            throw new UberEatsException(HttpStatus.SERVICE_UNAVAILABLE, "UBER_DISABLED");
        if (raw.length > 262144)
            throw new UberEatsException(HttpStatus.PAYLOAD_TOO_LARGE, "WEBHOOK_TOO_LARGE");
        if (!validSignature(raw, signature))
            throw new UberEatsException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID");
        if (!config.environment.equals(environment))
            throw new UberEatsException(HttpStatus.BAD_REQUEST, "WEBHOOK_ENVIRONMENT_MISMATCH");
        String eventId, type, storeId, orderId, hash;
        try {
            var body = json.readTree(raw);
            eventId = required(body.path("event_id").asText());
            type = required(body.path("event_type").asText());
            storeId = body.path("meta").path("user_id").asText("");
            orderId = body.path("meta").path("resource_id").asText("");
            if (EVENTS.contains(type)) {
                storeId = UUID.fromString(storeId).toString();
                orderId = UUID.fromString(orderId).toString();
            }
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (Exception ex) {
            throw new UberEatsException(HttpStatus.BAD_REQUEST, "WEBHOOK_MALFORMED");
        }
        LocalDateTime now = LocalDateTime.now();
        int inserted =
                events.insertIfAbsent(
                        config.environment,
                        eventId,
                        type,
                        storeId,
                        orderId,
                        hash,
                        EVENTS.contains(type) ? "PENDING" : "IGNORED",
                        now);
        if (inserted == 0) {
            var existing =
                    events.findByEnvironmentAndEventId(config.environment, eventId).orElseThrow();
            if (!hash.equals(existing.bodyHash))
                throw UberEatsException.conflict("WEBHOOK_EVENT_CONFLICT");
            return;
        }
        if (!EVENTS.contains(type)) return;
        var mapping =
                stores.findByEnvironmentAndUberStoreId(config.environment, storeId).orElse(null);
        if (mapping == null || !Boolean.TRUE.equals(mapping.enabled))
            return; // durable retry after configuration
        orders.insertIfAbsent(
                config.environment, mapping.id, mapping.storeId, storeId, orderId, eventId, now);
        var row = orders.findByEnvironmentAndUberOrderId(config.environment, orderId).orElseThrow();
        row = orders.lock(row.id).orElseThrow();
        entityManager.refresh(row, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!row.storeMappingId.equals(mapping.id))
            throw UberEatsException.conflict("WEBHOOK_STORE_MISMATCH");
        // Persist disruptive events immediately, even before GET or an in-flight acceptance
        // returns.
        if ("orders.cancel".equals(type)) {
            row.cancelled = true;
            row.cancelledAt = now;
            row.status =
                    row.localOrderId != null || row.acceptedBy != null
                            ? "CANCELLED_REVIEW_REQUIRED"
                            : "CANCELLED";
        } else if ("orders.customer_order_edit".equals(type)) {
            row.editRequired = true;
            row.status = "EDIT_REVIEW_REQUIRED";
        } else if ("orders.scheduled.notification".equals(type)
                && "RECEIVED".equals(row.status)
                && !events.existsByEnvironmentAndUberStoreIdAndUberOrderIdAndEventType(
                        config.environment, storeId, orderId, "orders.notification")) {
            row.scheduled = true;
        } else if ("orders.notification".equals(type)) {
            row.scheduled = false;
        }
        row.updatedAt = now;
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    private String required(String value) {
        if (value == null || value.isBlank() || value.length() > 255)
            throw new IllegalArgumentException();
        return value;
    }
}
