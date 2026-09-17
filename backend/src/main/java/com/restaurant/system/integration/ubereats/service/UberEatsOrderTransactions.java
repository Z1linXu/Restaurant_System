package com.restaurant.system.integration.ubereats.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.dto.UberOrderSnapshot;
import com.restaurant.system.integration.ubereats.entity.*;
import com.restaurant.system.integration.ubereats.mapping.UberEatsMenuMappingService;
import com.restaurant.system.integration.ubereats.repository.*;
import com.restaurant.system.modules.*;
import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.user.repository.StoreRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/**
 * Each public method commits ONE short database phase. No remote Uber calls in these transactions.
 */
@Service
public class UberEatsOrderTransactions {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final UberEatsProperties config;
    private final UberEatsInboxEvents inboxEvents;
    private final UberEatsOrderRepository orders;
    private final UberEatsStoreMappingRepository stores;
    private final StoreRepository localStores;
    private final UberEatsEventRepository events;
    private final UberEatsMenuMappingService mapping;
    private final ObjectMapper json;
    private final OrderService domain;
    private final OrderRepository localOrders;
    private final StoreModuleAccessEvaluator modules;
    private final AuditLogService audit;

    public UberEatsOrderTransactions(
            UberEatsProperties config,
            UberEatsOrderRepository orders,
            UberEatsStoreMappingRepository stores,
            StoreRepository localStores,
            UberEatsEventRepository events,
            UberEatsMenuMappingService mapping,
            ObjectMapper json,
            OrderService domain,
            OrderRepository localOrders,
            StoreModuleAccessEvaluator modules,
            AuditLogService audit,
            UberEatsInboxEvents inboxEvents) {
        this.inboxEvents = inboxEvents;
        this.config = config;
        this.orders = orders;
        this.stores = stores;
        this.localStores = localStores;
        this.events = events;
        this.mapping = mapping;
        this.json = json;
        this.domain = domain;
        this.localOrders = localOrders;
        this.modules = modules;
        this.audit = audit;
    }

    public UberEatsStoreMapping requireMapping(Long id) {
        var store =
                stores.findById(id)
                        .orElseThrow(() -> UberEatsException.conflict("STORE_MAPPING_MISSING"));
        var local =
                localStores
                        .findById(store.storeId)
                        .orElseThrow(() -> UberEatsException.conflict("STORE_MAPPING_INVALID"));
        if (!config.environment.equals(store.environment)
                || !Boolean.TRUE.equals(store.enabled)
                || !Objects.equals(local.organization_id, store.organizationId))
            throw UberEatsException.conflict("STORE_MAPPING_INVALID");
        return store;
    }

    public UberEatsOrder scoped(Long storeId, Long id) {
        var row =
                orders.findById(id)
                        .orElseThrow(() -> UberEatsException.conflict("UBER_ORDER_NOT_FOUND"));
        if (!storeId.equals(row.storeId) || !config.environment.equals(row.environment))
            throw new UberEatsException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "UBER_STORE_MISMATCH");
        return row;
    }

    @Transactional
    public boolean claimEvent(Long id) {
        var e = events.lock(id).orElseThrow();
        if (!"PENDING".equals(e.status) || e.nextAttemptAt.isAfter(LocalDateTime.now()))
            return false;
        e.nextAttemptAt = LocalDateTime.now().plusSeconds(90);
        events.save(e);
        return true;
    }

    @Transactional
    public void imported(Long eventId, UberOrderSnapshot snapshot) {
        var e = events.lock(eventId).orElseThrow();
        var store =
                stores.findByEnvironmentAndUberStoreId(config.environment, e.uberStoreId)
                        .orElseThrow(() -> UberEatsException.conflict("STORE_MAPPING_MISSING"));
        requireMapping(store.id);
        if (!e.uberOrderId.equals(snapshot.id()) || !e.uberStoreId.equals(snapshot.store_id()))
            throw UberEatsException.conflict("UBER_RESPONSE_STORE_MISMATCH");
        orders.insertIfAbsent(
                config.environment,
                store.id,
                store.storeId,
                store.uberStoreId,
                e.uberOrderId,
                e.eventId,
                LocalDateTime.now());
        var current =
                orders.findByEnvironmentAndUberOrderId(config.environment, e.uberOrderId)
                        .orElseThrow();
        var row = lockOrder(current.id);
        if (!row.storeMappingId.equals(store.id))
            throw UberEatsException.conflict("UBER_STORE_MISMATCH");
        if (row.acceptedBy == null
                && row.localOrderId == null
                && !Boolean.TRUE.equals(row.cancelled)
                && !Boolean.TRUE.equals(row.editRequired)
                && !"DENIED".equals(row.status)) {
            applySnapshot(row, snapshot);
            if ("orders.scheduled.notification".equals(e.eventType)
                    && "RECEIVED".equals(row.status)
                    && !events.existsByEnvironmentAndUberStoreIdAndUberOrderIdAndEventType(
                            config.environment,
                            e.uberStoreId,
                            e.uberOrderId,
                            "orders.notification")) row.scheduled = true;
            var result = mapping.map(store, snapshot);
            setMapping(row, result);
            if ("CANCELED".equals(snapshot.current_state())) {
                row.cancelled = true;
                row.cancelledAt = LocalDateTime.now();
                row.status = "CANCELLED";
            } else if (!"CREATED".equals(snapshot.current_state())) {
                row.status = "EXTERNAL_STATE_REVIEW_REQUIRED";
            } else
                row.status =
                        Boolean.TRUE.equals(row.scheduled)
                                ? "SCHEDULED_REVIEW_REQUIRED"
                                : result.valid() ? "PENDING" : "MAPPING_REQUIRED";
            row.updatedAt = LocalDateTime.now();
            orders.save(row);
            inboxEvents.changed(row.storeId);
        }
        e.status = "COMPLETED";
        e.errorCode = null;
        e.updatedAt = LocalDateTime.now();
        events.save(e);
    }

    @Transactional
    public void finishDisruptiveEvent(Long eventId) {
        var e = events.lock(eventId).orElseThrow();
        var store =
                stores.findByEnvironmentAndUberStoreId(config.environment, e.uberStoreId)
                        .orElseThrow(() -> UberEatsException.conflict("STORE_MAPPING_MISSING"));
        requireMapping(store.id);
        orders.insertIfAbsent(
                config.environment,
                store.id,
                store.storeId,
                store.uberStoreId,
                e.uberOrderId,
                e.eventId,
                LocalDateTime.now());
        var row =
                lockOrder(
                        orders.findByEnvironmentAndUberOrderId(config.environment, e.uberOrderId)
                                .orElseThrow()
                                .id);
        if (!row.storeMappingId.equals(store.id))
            throw UberEatsException.conflict("UBER_STORE_MISMATCH");
        if ("orders.cancel".equals(e.eventType)) {
            row.cancelled = true;
            row.cancelledAt = LocalDateTime.now();
            row.status =
                    row.localOrderId != null || row.acceptedBy != null
                            ? "CANCELLED_REVIEW_REQUIRED"
                            : "CANCELLED";
        } else {
            row.editRequired = true;
            row.status = "EDIT_REVIEW_REQUIRED";
        }
        row.updatedAt = LocalDateTime.now();
        orders.save(row);
        inboxEvents.changed(row.storeId);
        e.status = "COMPLETED";
        e.updatedAt = LocalDateTime.now();
        events.save(e);
    }

    @Transactional
    public void eventFailed(Long id, String code) {
        var e = events.lock(id).orElseThrow();
        e.attemptCount++;
        e.errorCode = code;
        e.updatedAt = LocalDateTime.now();
        e.nextAttemptAt = e.updatedAt.plusSeconds(Math.min(60, 5L * e.attemptCount));
        events.save(e);
    }

    public record Decision(UberEatsOrder order, boolean execute) {}

    @Transactional
    public Decision prepare(
            Long storeId,
            Long id,
            Long actor,
            UberOrderSnapshot snapshot,
            String action,
            String reason) {
        scoped(storeId, id);
        var row = lockOrder(id);
        if (row.localOrderId != null
                || "DENIED".equals(row.status)
                || Set.of("ACCEPTING", "DENYING", "UBER_ACCEPTED", "LOCAL_FAILED")
                        .contains(row.status)) return new Decision(row, false);
        requireUsable(row);
        if (!Set.of("PENDING", "MAPPING_REQUIRED", "RECEIVED").contains(row.status))
            throw UberEatsException.conflict("UBER_DECISION_REQUIRES_REVIEW");
        var store = requireMapping(row.storeMappingId);
        if (!row.uberOrderId.equals(snapshot.id())
                || !store.uberStoreId.equals(snapshot.store_id()))
            throw UberEatsException.conflict("UBER_RESPONSE_STORE_MISMATCH");
        if (!"CREATED".equals(snapshot.current_state()))
            throw UberEatsException.conflict("UBER_ORDER_NOT_CREATED");
        if (!snapshot.order_manager_client_id().isBlank()
                && !config.clientId.equals(snapshot.order_manager_client_id()))
            throw UberEatsException.conflict("UBER_NOT_ORDER_MANAGER");
        if ("ACCEPT".equals(action)) {
            modules.requireOperationalCapability(storeId, ModuleKeys.ORDERING_POS);
            modules.requireOperationalCapability(storeId, ModuleKeys.MENU);
            var result = mapping.map(store, snapshot);
            setMapping(row, result);
            applySnapshot(row, snapshot);
            if (!result.valid()) {
                row.status = "MAPPING_REQUIRED";
                orders.save(row);
                inboxEvents.changed(row.storeId);
                return new Decision(row, false);
            }
            result.request().created_by = actor;
            row.localRequestJson = encode(result.request());
            row.status = "ACCEPTING";
        } else {
            row.status = "DENYING";
            row.denyReason = reason;
        }
        row.acceptedBy = actor;
        row.lastError = null;
        row.updatedAt = LocalDateTime.now();
        row.nextAttemptAt = row.updatedAt.plusSeconds(90);
        orders.save(row);
        inboxEvents.changed(row.storeId);
        audit.recordSystem(
                storeId,
                actor,
                "Restaurant staff",
                null,
                "UBER_" + action + "_REQUESTED",
                "UBER_ORDER",
                row.id,
                "Uber order decision",
                Map.of("action", action),
                null);
        return new Decision(row, true);
    }

    @Transactional
    public void accepted(Long id) {
        var row = lockOrder(id);
        row.acceptedAt = LocalDateTime.now();
        if (!Boolean.TRUE.equals(row.cancelled)
                && !Boolean.TRUE.equals(row.editRequired)
                && row.localOrderId == null) row.status = "UBER_ACCEPTED";
        row.nextAttemptAt = LocalDateTime.now();
        row.updatedAt = LocalDateTime.now();
        row.lastError = null;
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    @Transactional
    public void denied(Long id) {
        var row = lockOrder(id);
        if (!Boolean.TRUE.equals(row.cancelled) && !Boolean.TRUE.equals(row.editRequired))
            row.status = "DENIED";
        row.updatedAt = LocalDateTime.now();
        row.lastError = null;
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    @Transactional
    public void remoteFailure(Long id, int httpStatus) {
        var row = lockOrder(id);
        row.lastError = "UBER_API_" + (httpStatus == 0 ? "UNAVAILABLE" : httpStatus);
        row.updatedAt = LocalDateTime.now();
        if (httpStatus >= 400
                && httpStatus < 500
                && httpStatus != 408
                && httpStatus != 409
                && httpStatus != 429
                && Set.of("ACCEPTING", "DENYING").contains(row.status)) {
            row.status = "PENDING";
            row.acceptedBy = null;
        }
        row.nextAttemptAt = row.updatedAt.plusSeconds(30);
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    @Transactional
    public UberEatsOrder submitLocal(Long id) {
        var row = lockOrder(id);
        if (row.localOrderId != null) return row;
        requireUsable(row);
        requireMapping(row.storeMappingId);
        if (!Set.of("UBER_ACCEPTED", "LOCAL_FAILED").contains(row.status)) return row;
        modules.requireOperationalCapability(row.storeId, ModuleKeys.ORDERING_POS);
        CreateOrderRequest request = decode(row.localRequestJson, CreateOrderRequest.class);
        if (!row.storeId.equals(request.store_id)
                || !"delivery".equals(request.order_type)
                || request.table_no != null
                || request.pickup_no != null)
            throw UberEatsException.conflict("FROZEN_REQUEST_SCOPE_INVALID");
        var response = domain.createOrReplaceDraftAndSubmit(request, null);
        var order = localOrders.findById(response.id).orElseThrow();
        order.external_source = "UBER_EATS";
        order.external_order_id = row.uberOrderId;
        order.external_display_id = row.displayId;
        localOrders.save(order);
        row.localOrderId = response.id;
        row.status = "ACCEPTED";
        row.lastError = null;
        row.updatedAt = LocalDateTime.now();
        orders.save(row);
        inboxEvents.changed(row.storeId);
        audit.recordSystem(
                row.storeId,
                row.acceptedBy,
                "Restaurant staff",
                null,
                "UBER_LOCAL_SUBMITTED",
                "UBER_ORDER",
                row.id,
                "Uber order entered production",
                Map.of("local_order_id", response.id),
                null);
        return row;
    }

    @Transactional
    public void localFailed(Long id) {
        var row = lockOrder(id);
        if (row.localOrderId != null
                || Boolean.TRUE.equals(row.cancelled)
                || Boolean.TRUE.equals(row.editRequired)) return;
        row.attemptCount++;
        row.status = row.attemptCount >= 10 ? "LOCAL_REVIEW_REQUIRED" : "LOCAL_FAILED";
        row.lastError = "LOCAL_SUBMISSION_FAILED";
        row.updatedAt = LocalDateTime.now();
        row.nextAttemptAt = row.updatedAt.plusSeconds(Math.min(300, 30L * row.attemptCount));
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    @Transactional
    public void recoveryFailed(Long id) {
        var row = lockOrder(id);
        row.lastError = "UBER_RECONCILIATION_UNAVAILABLE";
        row.nextAttemptAt = LocalDateTime.now().plusSeconds(60);
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    @Transactional
    public boolean claimRecovery(Long id) {
        var row = lockOrder(id);
        if (!Set.of("ACCEPTING", "DENYING", "UBER_ACCEPTED", "LOCAL_FAILED").contains(row.status)
                || row.nextAttemptAt.isAfter(LocalDateTime.now())) return false;
        row.nextAttemptAt = LocalDateTime.now().plusSeconds(90);
        orders.save(row);
        inboxEvents.changed(row.storeId);
        return true;
    }

    @Transactional
    public void review(Long id, String code) {
        var row = lockOrder(id);
        if (!Boolean.TRUE.equals(row.cancelled) && !Boolean.TRUE.equals(row.editRequired))
            row.status = "DECISION_REVIEW_REQUIRED";
        row.lastError = code;
        row.updatedAt = LocalDateTime.now();
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    @Transactional
    public void retryLocal(Long storeId, Long id) {
        scoped(storeId, id);
        var row = lockOrder(id);
        requireUsable(row);
        if (row.acceptedAt == null || row.localRequestJson == null || row.localOrderId != null)
            throw UberEatsException.conflict("LOCAL_RETRY_NOT_ALLOWED");
        row.status = "LOCAL_FAILED";
        row.attemptCount = 0;
        row.nextAttemptAt = LocalDateTime.now();
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    @Transactional
    public void remapStore(Long storeId) {
        for (var candidate :
                orders.findByEnvironmentAndStoreIdOrderByIdDesc(
                        config.environment,
                        storeId,
                        org.springframework.data.domain.PageRequest.of(0, 100))) {
            var row = lockOrder(candidate.id);
            if (!Set.of("PENDING", "MAPPING_REQUIRED").contains(row.status)
                    || row.rawOrderSnapshotJson == null) continue;
            var result =
                    mapping.map(
                            requireMapping(row.storeMappingId),
                            decode(row.rawOrderSnapshotJson, UberOrderSnapshot.class));
            setMapping(row, result);
            row.status = result.valid() ? "PENDING" : "MAPPING_REQUIRED";
            orders.save(row);
            inboxEvents.changed(storeId);
        }
    }

    private UberEatsOrder lockOrder(Long id) {
        var row = orders.lock(id).orElseThrow();
        // A prior scoped lookup / inbox query may have populated the first-level cache before the
        // lock.
        entityManager.refresh(row, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return row;
    }

    private void requireUsable(UberEatsOrder row) {
        if (Boolean.TRUE.equals(row.cancelled)
                || Boolean.TRUE.equals(row.editRequired)
                || Boolean.TRUE.equals(row.scheduled))
            throw UberEatsException.conflict("UBER_ORDER_REQUIRES_REVIEW");
    }

    private void applySnapshot(UberEatsOrder row, UberOrderSnapshot s) {
        row.rawOrderSnapshotJson = encode(s);
        row.displayId = s.display_id();
        row.fulfillmentType = s.fulfillment_type();
        row.placedAt = parseTime(s.placed_at());
        row.scheduledAt =
                Boolean.TRUE.equals(row.scheduled) ? parseTime(s.estimated_ready_at()) : null;
    }

    private void setMapping(UberEatsOrder row, UberEatsMenuMappingService.Result result) {
        row.mappingStatus = result.valid() ? "MAPPED" : "MAPPING_REQUIRED";
        row.mappingError = result.valid() ? null : encode(result.errors());
    }

    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("UBER_SNAPSHOT_SERIALIZATION_FAILED");
        }
    }

    public <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalStateException("UBER_SNAPSHOT_INVALID");
        }
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (Exception ex) {
            return null;
        }
    }
}
