package com.restaurant.system.printing.renderer;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class HotKitchenReceiptRenderer implements ReceiptRenderer {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> MODIFIER_PREFIXES = Set.of("+", "走", "少", "不要", "无");

    private final HotKitchenPrintEligibilityService eligibilityService;

    public HotKitchenReceiptRenderer(HotKitchenPrintEligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @Override
    public String getModuleCode() {
        return PrintModuleCode.HOT_KITCHEN;
    }

    @Override
    public String render(PrintRenderRequest request) {
        List<KitchenTask> tasks = eligibilityService.resolveHotKitchenTasks(request);
        if (tasks.isEmpty()) {
            return "";
        }

        Map<Long, OrderItem> itemById = new HashMap<>();
        if (request.order_items != null) {
            for (OrderItem item : request.order_items) {
                if (item != null && item.id != null) {
                    itemById.put(item.id, item);
                }
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("\n\n\n");
        if (Boolean.TRUE.equals(request.is_update_ticket)) {
            builder.append(PrintMarkup.large("UPDATED")).append("\n");
            builder.append("--------------------------------\n");
        }
        String topLabel = resolveTopDisplayLabel(request.order);
        if (topLabel != null) {
            builder.append(PrintMarkup.large(topLabel)).append("\n");
        }
        builder.append(PrintMarkup.large("HOT KITCHEN")).append("\n");
        builder.append("--------------------------------\n");

        for (KitchenTask task : tasks) {
            appendTask(builder, task, itemById.get(task.order_item_id));
        }

        builder.append("--------------------------------\n");
        builder.append(PrintMarkup.small(resolveTime(request.order))).append("\n");
        builder.append("--------------------------------\n\n");
        return builder.toString();
    }

    private void appendTask(StringBuilder builder, KitchenTask task, OrderItem item) {
        builder.append(PrintMarkup.doubleHeight(resolvePrimaryLine(task))).append("\n");
        String secondary = resolveSecondaryLine(task);
        if (secondary != null) {
            builder.append(PrintMarkup.doubleHeight(secondary)).append("\n");
        }
        String note = item == null ? null : normalize(item.notes);
        if (note != null) {
            builder.append(PrintMarkup.doubleHeight("备注：" + note)).append("\n");
        }
        builder.append("\n");
    }

    private String resolvePrimaryLine(KitchenTask task) {
        String itemName = fallback(task.item_name_snapshot_zh, task.item_name_snapshot_en, "Item");
        String special = normalize(task.special_instructions_snapshot);
        int quantity = task.quantity == null ? 1 : task.quantity;
        if (special != null && shouldUseSpecialAsPrimary(itemName, special)) {
            return special + " x" + quantity;
        }
        return itemName + " x" + quantity;
    }

    private String resolveSecondaryLine(KitchenTask task) {
        String itemName = fallback(task.item_name_snapshot_zh, task.item_name_snapshot_en, "Item");
        String special = normalize(task.special_instructions_snapshot);
        if (special == null || shouldUseSpecialAsPrimary(itemName, special)) {
            return null;
        }
        return special;
    }

    private boolean shouldUseSpecialAsPrimary(String itemName, String special) {
        if (special.contains("|")) {
            return true;
        }
        if (itemName.contains(special) && !special.equals(itemName)) {
            return true;
        }
        for (String prefix : MODIFIER_PREFIXES) {
            if (special.startsWith(prefix)) {
                return false;
            }
        }
        return special.length() <= 12;
    }

    private String resolveTopDisplayLabel(Order order) {
        if (order == null) {
            return null;
        }
        if (isTakeout(order)) {
            if (order.pickup_no != null && !order.pickup_no.isBlank()) {
                return order.pickup_no;
            }
            return null;
        }
        if (order.table_no != null && !order.table_no.isBlank()) {
            return "桌号：" + order.table_no;
        }
        return "桌号：Walk-in";
    }

    private boolean isTakeout(Order order) {
        return order != null && ("pickup".equalsIgnoreCase(order.order_type) || "takeout".equalsIgnoreCase(order.order_type));
    }

    private String resolveTime(Order order) {
        if (order != null && order.submitted_at != null) {
            return order.submitted_at.format(TIME_FORMATTER);
        }
        if (order != null && order.created_at != null) {
            return order.created_at.format(TIME_FORMATTER);
        }
        return java.time.LocalTime.now().format(TIME_FORMATTER);
    }

    private String fallback(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary.trim();
        }
        return fallback;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
