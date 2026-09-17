package com.restaurant.system.integration.ubereats.service;

import com.restaurant.system.integration.ubereats.client.*;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import com.restaurant.system.integration.ubereats.repository.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class UberEatsOrderImportService {
    private final UberEatsProperties config;
    private final UberEatsOrderClient client;
    private final UberEatsOrderNormalizer normalizer;
    private final UberEatsOrderTransactions tx;
    private final UberEatsEventRepository events;
    private final UberEatsOrderRepository orders;

    public UberEatsOrderImportService(
            UberEatsProperties config,
            UberEatsOrderClient client,
            UberEatsOrderNormalizer normalizer,
            UberEatsOrderTransactions tx,
            UberEatsEventRepository events,
            UberEatsOrderRepository orders) {
        this.config = config;
        this.client = client;
        this.normalizer = normalizer;
        this.tx = tx;
        this.events = events;
        this.orders = orders;
    }

    public void processEvents() {
        if (!config.enabled) return;
        for (var event :
                events.due(config.environment, LocalDateTime.now(), PageRequest.of(0, 10))) {
            if (!tx.claimEvent(event.id)) continue;
            try {
                if (Set.of("orders.cancel", "orders.customer_order_edit").contains(event.eventType))
                    tx.finishDisruptiveEvent(event.id);
                else
                    tx.imported(event.id, normalizer.normalize(client.getOrder(event.uberOrderId)));
            } catch (RuntimeException ex) {
                tx.eventFailed(event.id, safeCode(ex));
            }
        }
    }

    public UberEatsOrder decide(Long store, Long id, Long actor, String action, String reason) {
        if (!config.enabled) throw UberEatsException.conflict("UBER_DISABLED");
        var row = tx.scoped(store, id);
        if (row.localOrderId != null
                || "DENIED".equals(row.status)
                || Set.of("ACCEPTING", "DENYING", "UBER_ACCEPTED", "LOCAL_FAILED")
                        .contains(row.status)) return row;
        var snapshot = normalizer.normalize(client.getOrder(row.uberOrderId));
        var decision = tx.prepare(store, id, actor, snapshot, action, reason);
        if (!decision.execute()) return decision.order();
        try {
            if ("ACCEPT".equals(action)) {
                client.accept(row.uberOrderId, reference(row));
                tx.accepted(id);
                local(id);
            } else {
                client.deny(row.uberOrderId, reason);
                tx.denied(id);
            }
        } catch (UberEatsApiException ex) {
            tx.remoteFailure(id, ex.status);
        }
        return tx.scoped(store, id);
    }

    public void recover() {
        if (!config.enabled) return;
        for (var row : orders.due(config.environment, LocalDateTime.now(), PageRequest.of(0, 10))) {
            if (!tx.claimRecovery(row.id)) continue;
            try {
                if (Set.of("UBER_ACCEPTED", "LOCAL_FAILED").contains(row.status)) {
                    local(row.id);
                    continue;
                }
                var remote = normalizer.normalize(client.getOrder(row.uberOrderId));
                if (!row.uberStoreId.equals(remote.store_id())
                        || !row.uberOrderId.equals(remote.id())) {
                    tx.review(row.id, "UBER_RESPONSE_STORE_MISMATCH");
                    continue;
                }
                if ("ACCEPTING".equals(row.status)
                        && "ACCEPTED".equals(remote.current_state())
                        && reference(row).equals(remote.external_reference_id())) {
                    tx.accepted(row.id);
                    local(row.id);
                } else if ("DENYING".equals(row.status) && "DENIED".equals(remote.current_state()))
                    tx.denied(row.id);
                else tx.review(row.id, "AMBIGUOUS_DECISION_REQUIRES_OPERATOR");
            } catch (RuntimeException ex) {
                tx.recoveryFailed(row.id);
            }
        }
    }

    private void local(Long id) {
        try {
            tx.submitLocal(id);
        } catch (RuntimeException ex) {
            tx.localFailed(id);
        }
    }

    public static String reference(UberEatsOrder order) {
        return "rs-uber-" + order.id;
    }

    private String safeCode(RuntimeException ex) {
        return ex instanceof UberEatsApiException || ex instanceof UberEatsException
                ? ex.getMessage()
                : "UBER_IMPORT_FAILED";
    }
}
