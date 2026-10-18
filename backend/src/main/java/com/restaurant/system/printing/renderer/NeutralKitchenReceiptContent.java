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
        String title = "GRAB".equals(module) ? request.printing_rules.grabTitle() : module;
        result.append(PrintMarkup.large(title)).append("\n");
        if (Boolean.TRUE.equals(request.is_update_ticket))
            result.append(request.printing_rules.chineseReceipt() ? "追加单\n" : "UPDATED\n");
        if (request.order != null) {
            String label = request.order.table_no != null ? request.order.table_no : request.order.pickup_no;
            if (label != null) result.append(PrintMarkup.large(label)).append("\n");
        }
        for (KitchenTask task : tasks) {
            OrderItem item = request.order_items == null ? null : request.order_items.stream()
                .filter(candidate -> Objects.equals(candidate.id, task.order_item_id)).findFirst().orElse(null);
            // A component task may share its parent's order_item_id. Its frozen name and
            // total quantity describe the component, not another copy of the main dish.
            String fullName = task.item_name_snapshot_zh;
            if (fullName == null || fullName.isBlank()) fullName = task.item_name_snapshot_en;
            if ((fullName == null || fullName.isBlank()) && item != null)
                fullName = item.item_name_snapshot_zh == null ? item.item_name_snapshot_en : item.item_name_snapshot_zh;
            boolean component = task.component_option_id != null;
            String name = request.printing_rules.resolveItemAlias(module, item == null || component ? null : item.item_sku_snapshot, fullName);
            result.append(PrintMarkup.doubleHeight(Math.max(1, task.quantity == null ? 1 : task.quantity) + " x " + name)).append("\n");
            if (task.special_instructions_snapshot != null) result.append(task.special_instructions_snapshot).append("\n");
            if (!component && item != null && item.notes != null && !item.notes.isBlank()
                && (task.special_instructions_snapshot == null || !task.special_instructions_snapshot.contains(item.notes)))
                result.append(item.notes).append("\n");
        }
        ExternalKitchenFallbackContent.append(result, request.order_items,
            tasks.stream().map(task -> task.order_item_id).collect(java.util.stream.Collectors.toSet()));
        ExternalOrderNoteContent.append(result, request.order);
        return result.append("--------------------------------\n\n").toString();
    }
}
