package com.restaurant.system.printing.renderer;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import java.util.List;
import java.util.Objects;

/** Full snapshot labels for Neutral Stores. Dispatch, leases and saved payloads are unchanged. */
final class NeutralKitchenReceiptContent {
    private NeutralKitchenReceiptContent() {}

    static String render(PrintRenderRequest request, String module, List<KitchenTask> tasks) {
        if (tasks.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        ExternalOrderReceiptHeader.append(result, request.order);
        result.append(PrintMarkup.large(module)).append("\n");
        if (Boolean.TRUE.equals(request.is_update_ticket)) result.append("UPDATED\n");
        if (request.order != null) {
            String label = request.order.table_no != null ? request.order.table_no : request.order.pickup_no;
            if (label != null) result.append(PrintMarkup.large(label)).append("\n");
        }
        for (KitchenTask task : tasks) {
            OrderItem item = request.order_items == null ? null : request.order_items.stream()
                .filter(candidate -> Objects.equals(candidate.id, task.order_item_id)).findFirst().orElse(null);
            String fullName = item == null ? task.item_name_snapshot_zh : item.item_name_snapshot_zh;
            if (fullName == null || fullName.isBlank()) fullName = item == null ? task.item_name_snapshot_en : item.item_name_snapshot_en;
            String name = request.printing_rules.resolveItemAlias(module, item == null ? null : item.item_sku_snapshot, fullName);
            result.append(PrintMarkup.doubleHeight(Math.max(1, task.quantity == null ? 1 : task.quantity) + " x " + name)).append("\n");
            if (task.special_instructions_snapshot != null) result.append(task.special_instructions_snapshot).append("\n");
            if (item != null && item.notes != null && !item.notes.isBlank()
                && (task.special_instructions_snapshot == null || !task.special_instructions_snapshot.contains(item.notes)))
                result.append(item.notes).append("\n");
        }
        ExternalKitchenFallbackContent.append(result, request.order_items,
            tasks.stream().map(task -> task.order_item_id).collect(java.util.stream.Collectors.toSet()));
        ExternalOrderNoteContent.append(result, request.order);
        return result.append("--------------------------------\n\n").toString();
    }
}
