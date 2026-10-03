package com.restaurant.system.order.close;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.audit.entity.AuditLog;
import com.restaurant.system.audit.repository.AuditLogRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreDailyCloseService {
    public static final String ACTION = "AUTO_FINISHED_END_OF_DAY";
    public static final String REASON = "DAILY_23_30_AUTO_FINISH";
    private static final LocalTime CLOSE_TIME = LocalTime.of(23, 30);
    private static final Set<String> ACTIVE_STATUSES = Set.of("submitted", "preparing", "ready");
    private final StoreRepository stores;
    private final OrderRepository orders;
    private final StoreDailyCloseRunRepository runs;
    private final OrderService orderService;
    private final AuditLogRepository audits;
    private final ObjectMapper json;
    private final ZoneId defaultTimezone;

    public StoreDailyCloseService(StoreRepository stores, OrderRepository orders,
            StoreDailyCloseRunRepository runs, OrderService orderService,
            AuditLogRepository audits, ObjectMapper json,
            @Value("${app.daily-close.default-timezone:America/Toronto}") String defaultTimezone) {
        this.stores = stores;
        this.orders = orders;
        this.runs = runs;
        this.orderService = orderService;
        this.audits = audits;
        this.json = json;
        this.defaultTimezone = ZoneId.of(defaultTimezone);
    }

    @Transactional
    public int closeStore(Long storeId, Instant now) {
        Store store = stores.findById(storeId).orElse(null);
        if (!eligibleStore(store)) return 0;
        ZoneId zone = store.timezone == null || store.timezone.isBlank()
            ? defaultTimezone : ZoneId.of(store.timezone);
        var localNow = now.atZone(zone);
        if (localNow.toLocalTime().isBefore(CLOSE_TIME)) return 0;
        var date = localNow.toLocalDate();
        if (!runs.reserve(store.id, date, zone.getId(), now)) return 0;

        // Existing Order timestamps are LocalDateTime.now() in the application/JVM
        // timezone. Translate the Store's day boundaries into that storage clock.
        ZoneId storageZone = ZoneId.systemDefault();
        LocalDateTime start = date.atStartOfDay(zone).withZoneSameInstant(storageZone).toLocalDateTime();
        LocalDateTime end = date.plusDays(1).atStartOfDay(zone).withZoneSameInstant(storageZone).toLocalDateTime();
        int finished = 0;
        for (Long id : orders.findDailyCloseCandidateIds(store.id, start, end)) {
            Order order = orders.findByIdForUpdate(id);
            if (!eligibleOrder(order, store.id, start, end)) continue;
            orderService.completeOrder(id);
            AuditLog audit = new AuditLog();
            audit.store_id = store.id;
            audit.actor_name_snapshot = "Daily auto Finish";
            audit.actor_role_snapshot = "SYSTEM";
            audit.action = ACTION;
            audit.entity_type = "ORDER";
            audit.entity_id = id;
            audit.summary = "Automatically finished dine-in table at daily close";
            audit.created_at = LocalDateTime.ofInstant(now, storageZone);
            try {
                audit.metadata_json = json.writeValueAsString(Map.of(
                    "store_id", store.id, "order_id", id, "table_no", order.table_no,
                    "business_date", date.toString(), "timezone", zone.getId(),
                    "executed_at", now.toString(), "reason", REASON));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Could not serialize daily Finish audit", exception);
            }
            // A required audit is part of this transaction, rather than best-effort logging.
            audits.save(audit);
            finished++;
        }
        runs.complete(store.id, date, now, finished);
        return finished;
    }

    private boolean eligibleStore(Store store) {
        return store != null && "active".equalsIgnoreCase(store.status)
            && "ACTIVE".equalsIgnoreCase(store.lifecycle_status)
            && "BUSINESS".equalsIgnoreCase(store.store_kind);
    }

    private boolean eligibleOrder(Order order, Long storeId, LocalDateTime start, LocalDateTime end) {
        return order != null && Objects.equals(storeId, order.store_id)
            && "dine_in".equals(order.order_type) && ACTIVE_STATUSES.contains(order.status)
            && order.completed_at == null && order.table_no != null && !order.table_no.isBlank()
            && "IN_STORE".equals(order.financial_mode) && !"UBER_EATS".equals(order.external_source)
            && order.submitted_at != null && !order.submitted_at.isBefore(start) && order.submitted_at.isBefore(end);
    }
}
