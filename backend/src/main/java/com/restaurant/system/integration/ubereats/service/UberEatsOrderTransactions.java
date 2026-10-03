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
        modules.requireCapability(store.storeId, ModuleKeys.UBER_EATS);
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
        var binding = stores.findByEnvironmentAndUberStoreId(config.environment, e.uberStoreId).orElse(null);
        if (binding != null && !modules.evaluateCapability(binding.storeId, ModuleKeys.UBER_EATS).allowed()) {
            // Configuration drift must not keep this Store at the head of every worker page.
            e.nextAttemptAt = LocalDateTime.now().plusSeconds(90);
            events.save(e);
            return false;
        }
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
        store = stores.lock(store.id).orElseThrow();
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
        if ("KITCHEN_MIRROR".equals(row.processingMode)) {
            if ("orders.scheduled.notification".equals(e.eventType)
                    && row.releasedAt == null
                    && row.acceptedObservedAt == null
                    && !events.existsByEnvironmentAndUberStoreIdAndUberOrderIdAndEventType(
                            config.environment,
                            e.uberStoreId,
                            e.uberOrderId,
                            "orders.notification")) row.scheduled = true;
            importMirror(row, store, snapshot, "orders.release".equals(e.eventType), false);
        } else if (row.acceptedBy == null
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
        store = stores.lock(store.id).orElseThrow();
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
                    "KITCHEN_MIRROR".equals(row.processingMode)
                                    && (row.releasedAt != null || row.localOrderId != null)
                            ? "CANCELLED_AFTER_RELEASE"
                            : row.localOrderId != null || row.acceptedBy != null
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
        requireOrderManager(row);
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
        // Uber may redact this display field. The authenticated decision API enforces
        // nominated-manager permission; only its successful ACK permits local submission.
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
        if (row.rawOrderSnapshotJson != null)
            freezeFinancial(row, decode(row.rawOrderSnapshotJson, UberOrderSnapshot.class));
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
        boolean mirror = "KITCHEN_MIRROR".equals(row.processingMode);
        if (!(mirror
                        ? Set.of("MIRROR_READY", "MIRROR_LOCAL_FAILED")
                        : Set.of("UBER_ACCEPTED", "LOCAL_FAILED"))
                .contains(row.status)) return row;
        if (mirror
                && ((row.releasedAt == null && row.acceptedObservedAt == null)
                        || !Set.of("MAPPED", "PARTIALLY_MAPPED").contains(row.mappingStatus)))
            throw UberEatsException.conflict("MIRROR_RELEASE_NOT_READY");
        modules.requireOperationalCapability(row.storeId, ModuleKeys.ORDERING_POS);
        CreateOrderRequest request = decode(row.localRequestJson, CreateOrderRequest.class);
        if (!row.storeId.equals(request.store_id)
                || !"delivery".equals(request.order_type)
                || request.table_no != null
                || request.pickup_no != null)
            throw UberEatsException.conflict("FROZEN_REQUEST_SCOPE_INVALID");
        // Internal external metadata is never accepted by the ordinary public POS path.
        String orderNote = request.external_order_note_snapshot;
        if (!mirror) request.external_order_note_snapshot = null;
        var response =
                mirror
                        ? domain.createKitchenMirror(
                                request, row.uberOrderId, row.displayId, row.customerDisplayName)
                        : domain.createOrReplaceDraftAndSubmit(request, null);
        var order = localOrders.findById(response.id).orElseThrow();
        order.external_source = "UBER_EATS";
        order.external_order_id = row.uberOrderId;
        order.external_display_id = row.displayId;
        if (!mirror) order.external_order_note_snapshot = orderNote;
        localOrders.save(order);
        row.localOrderId = response.id;
        row.status = mirror ? ("PARTIALLY_MAPPED".equals(row.mappingStatus)
                ? "KITCHEN_SENT_WITH_MAPPING_WARNINGS" : "RELEASED_TO_KITCHEN") : "ACCEPTED";
        if (mirror) row.kitchenDispatchedAt = LocalDateTime.now(ZoneOffset.UTC);
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
        row.status =
                row.attemptCount >= 10
                        ? "LOCAL_REVIEW_REQUIRED"
                        : "KITCHEN_MIRROR".equals(row.processingMode)
                                ? "MIRROR_LOCAL_FAILED"
                                : "LOCAL_FAILED";
        row.lastError = "LOCAL_SUBMISSION_FAILED";
        row.updatedAt = LocalDateTime.now();
        row.nextAttemptAt = row.updatedAt.plusSeconds(Math.min(300, 30L * row.attemptCount));
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    @Transactional
    public void recoveryFailed(Long id, int remoteStatus) {
        var row = lockOrder(id);
        if (row.localOrderId != null
                || Boolean.TRUE.equals(row.cancelled)
                || Boolean.TRUE.equals(row.editRequired)) return;
        if (UberEatsAcceptancePolling.waiting(row)) {
            if (remoteStatus >= 400
                    && remoteStatus < 500
                    && !Set.of(401, 408, 429).contains(remoteStatus))
                acceptanceReview(row, "UBER_ACCEPTANCE_CHECK_REJECTED_" + remoteStatus);
            else if (UberEatsAcceptancePolling.expired(row, LocalDateTime.now(ZoneOffset.UTC)))
                acceptanceReview(row, "ACCEPTANCE_POLL_LIMIT_REACHED");
            else {
                row.lastError = "UBER_ACCEPTANCE_CHECK_UNAVAILABLE";
                // Do not amplify rate limits or transport failures with the fast CREATED cadence.
                row.nextAttemptAt =
                        LocalDateTime.now()
                                .plusSeconds(
                                        Math.max(
                                                60,
                                                UberEatsAcceptancePolling.delaySeconds(
                                                        row.acceptancePollCount)));
            }
        } else if ("KITCHEN_MIRROR".equals(row.processingMode)) {
            if (!Set.of("MIRROR_READY", "MIRROR_LOCAL_FAILED", "RELEASED_MAPPING_REQUIRED")
                    .contains(row.status)) return;
            row.attemptCount++;
            row.lastError = "UBER_RECONCILIATION_UNAVAILABLE";
            row.status = row.attemptCount >= 10 ? "LOCAL_REVIEW_REQUIRED" : row.status;
            row.nextAttemptAt = row.attemptCount >= 10 ? null : LocalDateTime.now().plusSeconds(60);
        } else {
            row.lastError = "UBER_RECONCILIATION_UNAVAILABLE";
            row.nextAttemptAt = LocalDateTime.now().plusSeconds(60);
        }
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    @Transactional
    public boolean claimRecovery(Long id) {
        var row = lockOrder(id);
        if (!Set.of(
                                "ACCEPTING",
                                "DENYING",
                                "UBER_ACCEPTED",
                                "LOCAL_FAILED",
                                "WAITING_FOR_ACCEPTANCE",
                                "WAITING_FOR_RELEASE",
                                "MIRROR_READY",
                                "MIRROR_LOCAL_FAILED",
                                "RELEASED_MAPPING_REQUIRED")
                        .contains(row.status)
                || row.nextAttemptAt == null
                || row.nextAttemptAt.isAfter(LocalDateTime.now())) return false;
        if (!modules.evaluateCapability(row.storeId, ModuleKeys.UBER_EATS).allowed()) {
            row.nextAttemptAt = LocalDateTime.now().plusSeconds(90);
            orders.save(row);
            return false;
        }
        if (UberEatsAcceptancePolling.waiting(row)) {
            initializeAcceptanceWindow(row);
            if (UberEatsAcceptancePolling.expired(row, LocalDateTime.now(ZoneOffset.UTC))) {
                acceptanceReview(row, "ACCEPTANCE_POLL_LIMIT_REACHED");
                orders.save(row);
                inboxEvents.changed(row.storeId);
                return false;
            }
            row.status = "WAITING_FOR_ACCEPTANCE";
            row.acceptancePollCount++;
        }
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
        if (("KITCHEN_MIRROR".equals(row.processingMode)
                        ? row.releasedAt == null && row.acceptedObservedAt == null
                        : row.acceptedAt == null)
                || row.localRequestJson == null
                || row.localOrderId != null)
            throw UberEatsException.conflict("LOCAL_RETRY_NOT_ALLOWED");
        row.status =
                "KITCHEN_MIRROR".equals(row.processingMode)
                        ? "MIRROR_LOCAL_FAILED"
                        : "LOCAL_FAILED";
        row.attemptCount = 0;
        row.nextAttemptAt = LocalDateTime.now();
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    @Transactional
    public void remapStore(Long storeId) {
        // Schedule every blocked mirror, including rows older than the inbox display window.
        // The existing due worker claims them in bounded batches and performs fresh remote GETs.
        orders.scheduleMirrorRemap(config.environment, storeId, LocalDateTime.now());
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

    public void requireOrderManager(UberEatsOrder row) {
        if ("KITCHEN_MIRROR".equals(row.processingMode)
                || "KITCHEN_MIRROR".equals(requireMapping(row.storeMappingId).processingMode))
            throw UberEatsException.conflict("KITCHEN_MIRROR_REMOTE_DECISION_DISABLED");
    }

    @Transactional
    public boolean refreshMirror(Long id, UberOrderSnapshot snapshot) {
        var row = lockOrder(id);
        var store = requireMapping(row.storeMappingId);
        if (!"KITCHEN_MIRROR".equals(row.processingMode))
            throw UberEatsException.conflict("MIRROR_MODE_REQUIRED");
        if (!row.uberOrderId.equals(snapshot.id()) || !row.uberStoreId.equals(snapshot.store_id()))
            throw UberEatsException.conflict("UBER_RESPONSE_STORE_MISMATCH");
        importMirror(row, store, snapshot, false, true);
        return "MIRROR_READY".equals(row.status) && row.localOrderId == null;
    }

    private void importMirror(
            UberEatsOrder row,
            UberEatsStoreMapping store,
            UberOrderSnapshot snapshot,
            boolean release,
            boolean poll) {
        if (row.localOrderId != null
                || Boolean.TRUE.equals(row.cancelled)
                || Boolean.TRUE.equals(row.editRequired)
                || Set.of(
                                "DENIED",
                                "LOCAL_REVIEW_REQUIRED",
                                "ACCEPTANCE_REVIEW_REQUIRED",
                                "EXTERNAL_STATE_REVIEW_REQUIRED")
                        .contains(row.status)) return;
        // A delayed notification cannot downgrade a checkpoint observed by either trigger.
        if (!release
                && !poll
                && !"SCHEDULED_REVIEW_REQUIRED".equals(row.status)
                && (row.releasedAt != null || row.acceptedObservedAt != null)) return;
        if (row.acceptedObservedAt != null && "CREATED".equals(snapshot.current_state())) return;
        applySnapshot(row, snapshot);
        row.currentState = snapshot.current_state();
        row.stateObservedAt = LocalDateTime.now(ZoneOffset.UTC);
        if (release) {
            if (row.releasedAt == null) row.releasedAt = row.stateObservedAt;
            row.scheduled = false;
        }
        row.nextAttemptAt = null;
        row.lastError = null;
        switch (snapshot.current_state()) {
            case "CANCELED" -> {
                row.cancelled = true;
                row.cancelledAt = LocalDateTime.now();
                row.status = row.releasedAt == null ? "CANCELLED" : "CANCELLED_AFTER_RELEASE";
            }
            case "DENIED" -> row.status = "DENIED";
            case "CREATED" -> {
                if (Boolean.TRUE.equals(row.scheduled)) row.status = "SCHEDULED_REVIEW_REQUIRED";
                else {
                    initializeAcceptanceWindow(row);
                    if (UberEatsAcceptancePolling.expired(row, row.stateObservedAt))
                        acceptanceReview(row, "ACCEPTANCE_POLL_LIMIT_REACHED");
                    else {
                        row.status = "WAITING_FOR_ACCEPTANCE";
                        row.nextAttemptAt =
                                LocalDateTime.now()
                                        .plusSeconds(
                                                UberEatsAcceptancePolling.delaySeconds(
                                                        row.acceptancePollCount));
                    }
                }
            }
            case "ACCEPTED" -> {
                if (row.acceptedObservedAt == null) row.acceptedObservedAt = row.stateObservedAt;
                if (Boolean.TRUE.equals(row.scheduled)) row.status = "SCHEDULED_REVIEW_REQUIRED";
                else prepareKitchenOnce(row, store, snapshot);
            }
            default -> {
                row.status = "EXTERNAL_STATE_REVIEW_REQUIRED";
                row.lastError =
                        "FINISHED".equals(snapshot.current_state())
                                ? "FINISHED_WITHOUT_KITCHEN_DISPATCH_REQUIRES_REVIEW"
                                : "UNKNOWN_UBER_STATE_REQUIRES_REVIEW";
            }
        }
        row.updatedAt = LocalDateTime.now();
        orders.save(row);
        inboxEvents.changed(row.storeId);
    }

    /** Both accepted observation and release enter this same idempotent kitchen gate. */
    private void prepareKitchenOnce(
            UberEatsOrder row, UberEatsStoreMapping store, UberOrderSnapshot snapshot) {
        var result = mapping.mapMirror(store, snapshot);
        setMapping(row, result);
        if (result.valid() && !result.warnings().isEmpty()) {
            row.mappingStatus = "PARTIALLY_MAPPED";
            row.mappingError = encode(result.warnings());
        }
        row.status = result.valid() ? "MIRROR_READY" : "RELEASED_MAPPING_REQUIRED";
        row.localRequestJson = result.valid() ? encode(result.request()) : null;
        // Incomplete mappings wait for an explicit mapping write, not perpetual Uber GET polling.
        row.nextAttemptAt = result.valid() ? LocalDateTime.now() : null;
    }

    private void initializeAcceptanceWindow(UberEatsOrder row) {
        if (row.acceptancePollStartedAt == null) {
            row.acceptancePollStartedAt = LocalDateTime.now(ZoneOffset.UTC);
            row.acceptancePollExpiresAt =
                    row.acceptancePollStartedAt.plusMinutes(UberEatsAcceptancePolling.MAX_MINUTES);
        }
    }

    private void acceptanceReview(UberEatsOrder row, String reason) {
        row.status = "ACCEPTANCE_REVIEW_REQUIRED";
        row.lastError = reason;
        row.nextAttemptAt = null;
        row.updatedAt = LocalDateTime.now();
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
        // Only verified acceptance freezes sales evidence. CREATED can still change before accept.
        // Once captured, even a held order/remap cannot rewrite the financial history.
        if ("ACCEPTED".equals(s.current_state()) || row.acceptedAt != null || row.acceptedObservedAt != null)
            freezeFinancial(row, s);
        row.rawOrderSnapshotJson = encode(s);
        row.displayId = s.display_id();
        row.customerDisplayName = s.customer_display_name();
        var charges = s.financial_charges();
        // Raw means the latest non-empty response, never a fabricated merge. Scalar fields retain
        // their last observed value when that optional amount is omitted on a subsequent GET.
        if (charges != null && !charges.isEmpty()) {
            row.rawFinancialSnapshotJson = encode(charges);
            Set<String> currencies = new HashSet<>();
            for (String key : List.of("total", "sub_total", "tax", "total_fee")) {
                String observed = charges.path(key).path("currency_code").asText("");
                if (observed.matches("[A-Z]{3}")) currencies.add(observed);
            }
            if (currencies.size() == 1) {
                String currency = currencies.iterator().next();
                if (row.financialCurrency != null && !currency.equals(row.financialCurrency)) {
                    // Never retain an amount across a currency change.
                    row.financialTotalMinor =
                            row.financialSubtotalMinor =
                                    row.financialTaxMinor = row.financialFeesMinor = null;
                }
                row.financialCurrency = currency;
                Long total = money(charges, "total", currency),
                        subtotal = money(charges, "sub_total", currency);
                Long tax = money(charges, "tax", currency),
                        fees = money(charges, "total_fee", currency);
                if (total != null) row.financialTotalMinor = total;
                if (subtotal != null) row.financialSubtotalMinor = subtotal;
                if (tax != null) row.financialTaxMinor = tax;
                if (fees != null) row.financialFeesMinor = fees;
            }
        }
        row.fulfillmentType = s.fulfillment_type();
        row.placedAt = parseTime(s.placed_at());
        row.scheduledAt =
                Boolean.TRUE.equals(row.scheduled) ? parseTime(s.estimated_ready_at()) : null;
    }

    private void freezeFinancial(UberEatsOrder row, UberOrderSnapshot snapshot) {
        if (row.itemFinancialSnapshotJson == null && snapshot.item_financial_snapshot() != null)
            row.itemFinancialSnapshotJson = encode(snapshot.item_financial_snapshot());
    }

    private Long money(
            com.fasterxml.jackson.databind.JsonNode charges, String field, String currency) {
        if (charges == null || !currency.equals(charges.path(field).path("currency_code").asText()))
            return null;
        var amount = charges.path(field).path("amount");
        return amount.isIntegralNumber() && amount.canConvertToLong() ? amount.longValue() : null;
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
