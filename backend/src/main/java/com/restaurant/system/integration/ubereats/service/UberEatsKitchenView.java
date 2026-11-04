package com.restaurant.system.integration.ubereats.service;

import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.integration.ubereats.repository.UberEatsInboxReadRepository;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Read-only projection of durable kitchen jobs; cancel/edit warnings take precedence over printing.
 */
@Service
public class UberEatsKitchenView {
    public record State(String status, String grab, String hotKitchen) {}

    public record Content(List<String> mappedItems, List<String> rawItems, String orderNote) {}
    private final UberEatsInboxReadRepository reads;
    private final UberEatsOrderTransactions tx;

    public UberEatsKitchenView(UberEatsInboxReadRepository reads, UberEatsOrderTransactions tx) {
        this.reads = reads; this.tx = tx;
    }

    public State state(UberEatsOrder row) { return states(List.of(row)).get(row); }

    /** At most one job and one missing-module Outbox read per Store, independent of row count. */
    public Map<UberEatsOrder,State> states(List<UberEatsOrder> rows) {
        Map<UberEatsOrder,State> result=new IdentityHashMap<>();
        Map<Long,List<UberEatsOrder>> stores=new LinkedHashMap<>();
        for (var row:rows) {
            if (row.localOrderId==null) result.put(row,new State(row.status,"WAITING","WAITING"));
            else stores.computeIfAbsent(row.storeId,ignored->new ArrayList<>()).add(row);
        }
        stores.forEach((store,orders)->{
            var ids=orders.stream().map(row->row.localOrderId).distinct().toList();
            Map<String,String> printed=new HashMap<>();
            reads.latestJobs(store,ids).forEach(job->printed.put(job.orderId()+":"+job.module(),job.status()));
            Set<String> missing=new LinkedHashSet<>();
            for (var row:orders) for (String module:List.of("GRAB","HOT_KITCHEN"))
                if (!printed.containsKey(row.localOrderId+":"+module)) missing.add("submit:"+row.localOrderId+":"+module);
            Map<String,String> dispatches=new HashMap<>();
            reads.dispatches(store,missing).forEach(event->dispatches.put(event.sourceKey(),event.status()));
            for (var row:orders) {
                String grab=module(row,"GRAB",printed,dispatches);
                String hot=module(row,"HOT_KITCHEN",printed,dispatches);
                result.put(row,project(row,grab,hot));
            }
        });
        return result;
    }

    private State project(UberEatsOrder row,String grab,String hot) {
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

    private String module(UberEatsOrder row,String module,Map<String,String> printed,Map<String,String> dispatches) {
        String status=printed.get(row.localOrderId+":"+module);
        if (status!=null) return status;
        return dispatches.getOrDefault("submit:"+row.localOrderId+":"+module,
            "HOT_KITCHEN".equals(module)?"NOT_REQUIRED":"WAITING");
    }

    public Content content(UberEatsOrder row) {
        if (row.localRequestJson==null) return new Content(List.of(),List.of(),null);
        var request=tx.decode(row.localRequestJson,CreateOrderRequest.class);
        return new Content(mappedItems(request),rawItems(request),request.external_order_note_snapshot);
    }

    public List<String> mappedItems(UberEatsOrder row) {
        if (row.localRequestJson == null) return List.of();
        return content(row).mappedItems();
    }

    private List<String> mappedItems(CreateOrderRequest request) {
        return request.items.stream()
                .filter(item -> item.external_kitchen_snapshot == null || !item.external_kitchen_snapshot.rawRoot())
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
    /** New imports freeze this separately; null identifies legacy requests without this field. */
    public String orderNote(UberEatsOrder row) {
        if (row.localRequestJson == null) return null;
        return content(row).orderNote();
    }

    public List<String> rawItems(UberEatsOrder row) {
        if (row.localRequestJson == null) return List.of();
        return content(row).rawItems();
    }

    private List<String> rawItems(CreateOrderRequest request) {
        List<String> lines = new ArrayList<>();
        for (var item : request.items) {
            var raw = item.external_kitchen_snapshot;
            if (raw == null) continue;
            lines.add((raw.rawRoot() ? raw.itemName() : item.item_name_snapshot_zh) + " ×" + item.quantity);
            appendRaw(lines, raw.modifiers(), 1);
        }
        return List.copyOf(lines);
    }

    private void appendRaw(List<String> lines, List<com.restaurant.system.order.dto.ExternalKitchenSnapshot.Modifier> modifiers, int multiplier) {
        for (var raw : modifiers) {
            if (raw.parentContext() != null && !raw.parentContext().isEmpty())
                lines.add("选项归属：" + raw.parentContext().stream().map(c -> c.name())
                        .collect(java.util.stream.Collectors.joining(" → ")));
            lines.add((raw.removed() ? "去除 " : "") + raw.name() + " ×" + Math.multiplyExact(multiplier, raw.quantity()));
            appendRaw(lines, raw.modifiers(), Math.multiplyExact(multiplier, raw.quantity()));
        }
    }

}
