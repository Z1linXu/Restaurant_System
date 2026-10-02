package com.restaurant.system.integration.ubereats.service;

import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.repository.*;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Read-only projection of durable kitchen jobs; cancel/edit warnings take precedence over printing.
 */
@Service
public class UberEatsKitchenView {
    public record State(String status, String grab, String hotKitchen) {}

    private final PrintJobRepository jobs;
    private final OrderDispatchOutboxRepository outbox;
    private final UberEatsOrderTransactions tx;

    public UberEatsKitchenView(
            PrintJobRepository jobs,
            OrderDispatchOutboxRepository outbox,
            UberEatsOrderTransactions tx) {
        this.jobs = jobs;
        this.outbox = outbox;
        this.tx = tx;
    }

    public State state(UberEatsOrder row) {
        if (row.localOrderId == null) return new State(row.status, "WAITING", "WAITING");
        var printed = jobs.findAllByStoreIdAndOrderId(row.storeId, row.localOrderId);
        String grab = module(row, "GRAB", printed);
        String hot = module(row, "HOT_KITCHEN", printed);
        String status = row.status;
        if ("KITCHEN_MIRROR".equals(row.processingMode) && "RELEASED_TO_KITCHEN".equals(status)) {
            var required =
                    List.of(grab, hot).stream().filter(s -> !"NOT_REQUIRED".equals(s)).toList();
            long done = required.stream().filter("PRINTED"::equals).count();
            if (!required.isEmpty() && done == required.size()) status = "PRINTED";
            else if (done > 0) status = "PRINT_PARTIAL";
            else if (required.stream()
                    .anyMatch(
                            s ->
                                    Set.of(
                                                    "FAILED",
                                                    "CANCELLED",
                                                    "POLICY_BLOCKED",
                                                    "CAPABILITY_UNAVAILABLE",
                                                    "SKIPPED")
                                            .contains(s))) status = "PRINT_FAILED";
        }
        return new State(status, grab, hot);
    }

    private String module(UberEatsOrder row, String module, List<PrintJob> printed) {
        var latest = printed.stream().filter(job -> module.equals(job.module_code)).findFirst();
        if (latest.isPresent()) return latest.get().status;
        return outbox.findBySourceKey("submit:" + row.localOrderId + ":" + module)
                .map(event -> event.status)
                .orElse("HOT_KITCHEN".equals(module) ? "NOT_REQUIRED" : "WAITING");
    }

    public List<String> mappedItems(UberEatsOrder row) {
        if (row.localRequestJson == null) return List.of();
        var request = tx.decode(row.localRequestJson, CreateOrderRequest.class);
        return request.items.stream()
                .map(
                        item ->
                                item.item_name_snapshot_zh
                                        + " ×"
                                        + item.quantity
                                        + (item.options.isEmpty()
                                                ? ""
                                                : " · "
                                                        + String.join(
                                                                " / ",
                                                                item.options.stream()
                                                                        .map(
                                                                                option ->
                                                                                        option.option_name_snapshot_zh)
                                                                        .filter(Objects::nonNull)
                                                                        .toList())))
                .toList();
    }
}
