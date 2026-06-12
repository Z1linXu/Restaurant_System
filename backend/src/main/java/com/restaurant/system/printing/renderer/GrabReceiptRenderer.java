package com.restaurant.system.printing.renderer;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GrabReceiptRenderer implements ReceiptRenderer {

    private static final Set<String> GRAB_STATIONS = Set.of("NOODLE", "WOK", "COLD", "DEEPFRIED");
    private static final Set<String> MODIFIER_PREFIXES = Set.of("+", "走", "少", "不要", "无");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public String getModuleCode() {
        return PrintModuleCode.GRAB;
    }

    @Override
    public String render(PrintRenderRequest request) {
        Order order = request.order;
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append(center("GRAB TICKET", 32)).append("\n");
        builder.append("--------------------------------\n");
        builder.append("Table/Pickup: ").append(resolveDisplayLabel(order)).append("\n");
        builder.append("Order Type: ").append("pickup".equals(order.order_type) ? "Takeout" : "Dine-in").append("\n");
        if (order.submitted_at != null) {
            builder.append("Submitted: ").append(order.submitted_at.format(TIME_FORMATTER)).append("\n");
        } else if (order.created_at != null) {
            builder.append("Created: ").append(order.created_at.format(TIME_FORMATTER)).append("\n");
        }
        builder.append("--------------------------------\n");

        Map<Long, OrderItem> orderItemById = new HashMap<>();
        if (request.order_items != null) {
            for (OrderItem orderItem : request.order_items) {
                if (orderItem != null && orderItem.id != null) {
                    orderItemById.put(orderItem.id, orderItem);
                }
            }
        }

        List<KitchenTask> tasks = request.kitchen_tasks == null ? List.of() : request.kitchen_tasks.stream()
            .filter(task -> task != null && GRAB_STATIONS.contains(task.station_code))
            .filter(task -> !"cancelled".equals(task.status))
            .sorted(Comparator.comparingInt((KitchenTask task) -> resolveSortPriority(task, orderItemById.get(task.order_item_id)))
                .thenComparing((KitchenTask task) -> task.created_at, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(task -> task.id))
            .toList();
        if (tasks.isEmpty()) {
            return "";
        }

        for (KitchenTask task : tasks) {
            builder.append(PrintMarkup.doubleHeight(resolvePrimaryKitchenLine(task)))
                .append("\n");
            String secondary = resolveSecondaryKitchenLine(task);
            if (secondary != null) {
                builder.append(PrintMarkup.doubleHeight("  " + secondary)).append("\n");
            }
            String itemNote = resolveItemNote(orderItemById.get(task.order_item_id));
            if (itemNote != null) {
                builder.append(PrintMarkup.doubleHeight("备注：" + itemNote)).append("\n");
            }
        }

        builder.append("--------------------------------\n");
        builder.append("No order number printed.\n\n");
        return builder.toString();
    }

    private String resolveDisplayLabel(Order order) {
        if (order.table_no != null && !order.table_no.isBlank()) {
            return order.table_no;
        }
        if (order.pickup_no != null && !order.pickup_no.isBlank()) {
            return order.pickup_no;
        }
        return "Walk-in";
    }

    private String center(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        int padding = (width - value.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + value;
    }

    private int resolveSortPriority(KitchenTask task, OrderItem orderItem) {
        if ("COLD".equals(task.station_code)) {
            return 1;
        }
        if ("DEEPFRIED".equals(task.station_code)) {
            return 2;
        }
        if ("NOODLE".equals(task.station_code) || "WOK".equals(task.station_code)) {
            return 3;
        }
        if (orderItem != null && orderItem.category_code_snapshot != null) {
            return switch (orderItem.category_code_snapshot) {
                case "SIDE", "COLD_APPETIZER" -> 1;
                case "FRIED", "DEEPFRIED" -> 2;
                case "SOUP_NOODLE", "DRY_NOODLE", "FRIED_NOODLE" -> 3;
                default -> 4;
            };
        }
        return 4;
    }

    private String resolvePrimaryKitchenLine(KitchenTask task) {
        String itemName = fallback(task.item_name_snapshot_zh, task.item_name_snapshot_en, "Item");
        String special = normalize(task.special_instructions_snapshot);
        int quantity = task.quantity == null ? 1 : task.quantity;

        if (special == null) {
            return itemName + " x" + quantity;
        }
        if (shouldUseSpecialAsPrimary(itemName, special)) {
            return special + " x" + quantity;
        }
        return itemName + " x" + quantity;
    }

    private String resolveSecondaryKitchenLine(KitchenTask task) {
        String itemName = fallback(task.item_name_snapshot_zh, task.item_name_snapshot_en, "Item");
        String special = normalize(task.special_instructions_snapshot);
        if (special == null) {
            return null;
        }
        if (shouldUseSpecialAsPrimary(itemName, special)) {
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

    private String resolveItemNote(OrderItem orderItem) {
        if (orderItem == null) {
            return null;
        }
        return normalize(orderItem.notes);
    }
}
