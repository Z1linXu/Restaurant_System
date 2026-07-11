package com.restaurant.system.printing.renderer;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

        Map<Long, List<OrderItemOption>> optionsByItemId = new HashMap<>();
        if (request.order_item_options != null) {
            for (OrderItemOption option : request.order_item_options) {
                if (option != null && option.order_item_id != null) {
                    optionsByItemId.computeIfAbsent(option.order_item_id, ignored -> new ArrayList<>()).add(option);
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

        for (AggregatedHotKitchenTask task : aggregateTasks(tasks, itemById, optionsByItemId)) {
            appendTask(builder, task);
        }

        builder.append("--------------------------------\n");
        if (isTakeout(request.order)) {
            builder.append(PrintMarkup.large("外卖 / TAKEOUT")).append("\n");
        }
        builder.append(PrintMarkup.small(resolveTime(request.order))).append("\n");
        builder.append("--------------------------------\n\n");
        return builder.toString();
    }

    private void appendTask(StringBuilder builder, AggregatedHotKitchenTask task) {
        if (task.noodleConfig() != null) {
            builder.append(PrintMarkup.doubleHeight(KitchenNoodlePrintFormatter.formatLine(task.noodleConfig(), task.quantity()))).append("\n");
            builder.append("\n");
            return;
        }
        builder.append(PrintMarkup.doubleHeight(resolvePrimaryLine(task.representative(), task.quantity()))).append("\n");
        String secondary = resolveSecondaryLine(task.representative());
        if (secondary != null) {
            builder.append(PrintMarkup.doubleHeight(secondary)).append("\n");
        }
        String note = task.item() == null ? null : normalize(task.item().notes);
        if (note != null) {
            builder.append(PrintMarkup.doubleHeight("备注：" + note)).append("\n");
        }
        builder.append("\n");
    }

    private List<AggregatedHotKitchenTask> aggregateTasks(
        List<KitchenTask> tasks,
        Map<Long, OrderItem> itemById,
        Map<Long, List<OrderItemOption>> optionsByItemId
    ) {
        Map<HotKitchenGroupKey, AggregatedHotKitchenTask> grouped = new LinkedHashMap<>();
        for (KitchenTask task : tasks) {
            OrderItem item = itemById.get(task.order_item_id);
            List<OrderItemOption> options = optionsByItemId.getOrDefault(task.order_item_id, List.of());
            KitchenNoodlePrintFormatter.NoodleConfig noodleConfig = KitchenNoodlePrintFormatter.isNoodleTask(task, item)
                ? KitchenNoodlePrintFormatter.buildConfig(task, item, KitchenNoodlePrintFormatter::normalizeModifierSegment)
                : null;
            HotKitchenGroupKey key = buildGroupKey(task, item, options, noodleConfig);
            grouped.compute(key, (ignored, existing) -> {
                int quantity = task.quantity == null ? 1 : task.quantity;
                if (existing == null) {
                    return new AggregatedHotKitchenTask(task, item, quantity, noodleConfig);
                }
                return existing.addQuantity(quantity);
            });
        }
        return new ArrayList<>(grouped.values());
    }

    private HotKitchenGroupKey buildGroupKey(
        KitchenTask task,
        OrderItem item,
        List<OrderItemOption> options,
        KitchenNoodlePrintFormatter.NoodleConfig noodleConfig
    ) {
        if (noodleConfig != null) {
            KitchenNoodlePrintFormatter.NoodleGroupKey noodleKey = KitchenNoodlePrintFormatter.buildGroupKey(
                task,
                item,
                options,
                noodleConfig
            );
            return new HotKitchenGroupKey(
                noodleKey.menuItemId(),
                noodleKey.categoryCode(),
                item == null ? null : item.combo_role,
                noodleKey.stationCode(),
                noodleKey.displayText(),
                noodleKey.notes(),
                noodleKey.optionKeys()
            );
        }

        List<String> optionKeys = options.stream()
            .sorted(Comparator
                .comparing((OrderItemOption option) -> stable(option.option_group_snapshot))
                .thenComparing(option -> stable(option.option_type_snapshot))
                .thenComparing(option -> stable(option.option_code_snapshot))
                .thenComparing(option -> option.option_id == null ? Long.MAX_VALUE : option.option_id)
                .thenComparing(option -> option.id == null ? Long.MAX_VALUE : option.id))
            .map(this::buildStableOptionKey)
            .toList();
        return new HotKitchenGroupKey(
            item == null ? null : item.menu_item_id,
            item == null ? null : item.category_code_snapshot,
            item == null ? null : item.combo_role,
            stable(task.station_code),
            stable(task.special_instructions_snapshot),
            stable(item == null ? null : item.notes),
            optionKeys
        );
    }

    private String buildStableOptionKey(OrderItemOption option) {
        return String.join("|",
            stable(option.option_group_snapshot),
            stable(option.option_type_snapshot),
            stable(option.option_code_snapshot),
            String.valueOf(option.option_id),
            String.valueOf(option.parent_option_id_snapshot),
            String.valueOf(option.quantity == null ? 1 : option.quantity),
            String.valueOf(option.price_delta == null ? BigDecimal.ZERO : option.price_delta.stripTrailingZeros())
        );
    }

    private String resolvePrimaryLine(KitchenTask task, int quantity) {
        String itemName = fallback(task.item_name_snapshot_zh, task.item_name_snapshot_en, "Item");
        String special = normalize(task.special_instructions_snapshot);
        if (special != null && shouldUseSpecialAsPrimary(itemName, special)) {
            return special + " ×" + quantity;
        }
        return itemName + " ×" + quantity;
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
            return "桌号：" + PrintTableDisplayFormatter.formatSplitTableLabel(order.table_no);
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

    private String stable(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private record HotKitchenGroupKey(
        Long menuItemId,
        String categoryCode,
        String comboRole,
        String stationCode,
        String specialInstructions,
        String notes,
        List<String> optionKeys
    ) {
    }

    private record AggregatedHotKitchenTask(
        KitchenTask representative,
        OrderItem item,
        int quantity,
        KitchenNoodlePrintFormatter.NoodleConfig noodleConfig
    ) {
        AggregatedHotKitchenTask addQuantity(int delta) {
            return new AggregatedHotKitchenTask(representative, item, quantity + delta, noodleConfig);
        }
    }
}
