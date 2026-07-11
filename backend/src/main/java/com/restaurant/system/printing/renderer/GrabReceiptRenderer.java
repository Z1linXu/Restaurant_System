package com.restaurant.system.printing.renderer;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GrabReceiptRenderer implements ReceiptRenderer {

    private static final Set<String> GRAB_STATIONS = Set.of("NOODLE", "WOK", "COLD", "DEEPFRIED");
    private static final Set<String> MODIFIER_PREFIXES = Set.of("+", "走", "少", "不要", "无");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String getModuleCode() {
        return PrintModuleCode.GRAB;
    }

    @Override
    public String render(PrintRenderRequest request) {
        Order order = request.order;
        StringBuilder builder = new StringBuilder();
        builder.append("\n\n\n\n");
        if (Boolean.TRUE.equals(request.is_update_ticket)) {
            builder.append(PrintMarkup.large("UPDATED")).append("\n");
            builder.append("--------------------------------\n");
        }
        String topLabel = resolveTopDisplayLabel(order);
        if (topLabel != null) {
            builder.append(PrintMarkup.large(topLabel)).append("\n");
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

        Map<Long, List<OrderItemOption>> optionsByItemId = new HashMap<>();
        if (request.order_item_options != null) {
            for (OrderItemOption option : request.order_item_options) {
                if (option != null && option.order_item_id != null) {
                    optionsByItemId.computeIfAbsent(option.order_item_id, ignored -> new ArrayList<>()).add(option);
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

        Map<SideGroupKey, SideGroup> sideGroups = buildSideGroups(tasks, orderItemById);
        Set<SideGroupKey> printedSideGroups = new LinkedHashSet<>();
        Map<KitchenNoodlePrintFormatter.NoodleGroupKey, NoodleGroup> noodleGroups = buildNoodleGroups(tasks, orderItemById, optionsByItemId);
        Set<KitchenNoodlePrintFormatter.NoodleGroupKey> printedNoodleGroups = new LinkedHashSet<>();

        for (KitchenTask task : tasks) {
            OrderItem orderItem = orderItemById.get(task.order_item_id);
            if (isSideTask(task, orderItem)) {
                SideGroupKey key = buildSideGroupKey(task, orderItem);
                if (!printedSideGroups.add(key)) {
                    continue;
                }
                SideGroup sideGroup = sideGroups.get(key);
                appendPrintLines(builder, sideGroup.toPrintLines());
                continue;
            }
            if (KitchenNoodlePrintFormatter.isNoodleTask(task, orderItem)) {
                KitchenNoodlePrintFormatter.NoodleConfig config = KitchenNoodlePrintFormatter.buildConfig(task, orderItem, this::normalizeNoodleConfigSegment);
                KitchenNoodlePrintFormatter.NoodleGroupKey key = KitchenNoodlePrintFormatter.buildGroupKey(
                    task,
                    orderItem,
                    optionsByItemId.getOrDefault(task.order_item_id, List.of()),
                    config
                );
                if (!printedNoodleGroups.add(key)) {
                    continue;
                }
                NoodleGroup noodleGroup = noodleGroups.get(key);
                appendPrintLines(builder, List.of(KitchenNoodlePrintFormatter.formatLine(noodleGroup.config(), noodleGroup.quantity())));
                continue;
            }
            appendPrintLines(builder, simplifyGreenOptions(buildItemLines(task, orderItem)));
        }

        builder.append("--------------------------------\n");
        if (isTakeout(order)) {
            builder.append(PrintMarkup.large("外卖")).append("\n");
        }
        builder.append(PrintMarkup.small(resolveTime(order))).append("\n");
        builder.append("--------------------------------\n\n");
        return builder.toString();
    }

    private String resolveTopDisplayLabel(Order order) {
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
        return "pickup".equalsIgnoreCase(order.order_type) || "takeout".equalsIgnoreCase(order.order_type);
    }

    private String resolveTime(Order order) {
        if (order.submitted_at != null) {
            return order.submitted_at.format(TIME_FORMATTER);
        }
        if (order.created_at != null) {
            return order.created_at.format(TIME_FORMATTER);
        }
        return java.time.LocalTime.now().format(TIME_FORMATTER);
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

    private List<String> buildItemLines(KitchenTask task, OrderItem orderItem) {
        List<String> lines = new ArrayList<>();
        lines.add(resolvePrimaryKitchenLine(task));
        String secondary = resolveSecondaryKitchenLine(task);
        if (secondary != null) {
            lines.add(secondary);
        }
        String itemNote = resolveItemNote(orderItem);
        if (itemNote != null) {
            lines.add("备注：" + itemNote);
        }
        return lines;
    }

    private boolean isSideTask(KitchenTask task, OrderItem orderItem) {
        if ("COLD".equals(task.station_code)) {
            return true;
        }
        return orderItem != null && ("SIDE".equals(orderItem.category_code_snapshot) || "COLD_APPETIZER".equals(orderItem.category_code_snapshot));
    }

    private void appendPrintLines(StringBuilder builder, List<String> lines) {
        for (String line : lines) {
            builder.append(PrintMarkup.doubleHeight(line)).append("\n");
        }
        builder.append("\n");
    }

    private Map<SideGroupKey, SideGroup> buildSideGroups(List<KitchenTask> tasks, Map<Long, OrderItem> orderItemById) {
        Map<SideGroupKey, SideGroup> groups = new LinkedHashMap<>();
        for (KitchenTask task : tasks) {
            OrderItem orderItem = orderItemById.get(task.order_item_id);
            if (!isSideTask(task, orderItem)) {
                continue;
            }
            SideGroupKey key = buildSideGroupKey(task, orderItem);
            groups.computeIfAbsent(key, ignored -> new SideGroup(key.displayName(), key.demands()))
                .addQuantity(task.quantity == null ? 1 : task.quantity);
        }
        return groups;
    }

    private Map<KitchenNoodlePrintFormatter.NoodleGroupKey, NoodleGroup> buildNoodleGroups(
        List<KitchenTask> tasks,
        Map<Long, OrderItem> orderItemById,
        Map<Long, List<OrderItemOption>> optionsByItemId
    ) {
        Map<KitchenNoodlePrintFormatter.NoodleGroupKey, NoodleGroup> groups = new LinkedHashMap<>();
        for (KitchenTask task : tasks) {
            OrderItem orderItem = orderItemById.get(task.order_item_id);
            if (!KitchenNoodlePrintFormatter.isNoodleTask(task, orderItem)) {
                continue;
            }
            KitchenNoodlePrintFormatter.NoodleConfig config = KitchenNoodlePrintFormatter.buildConfig(task, orderItem, this::normalizeNoodleConfigSegment);
            KitchenNoodlePrintFormatter.NoodleGroupKey key = KitchenNoodlePrintFormatter.buildGroupKey(
                task,
                orderItem,
                optionsByItemId.getOrDefault(task.order_item_id, List.of()),
                config
            );
            groups.compute(key, (ignored, existing) -> {
                int quantity = task.quantity == null ? 1 : task.quantity;
                if (existing == null) {
                    return new NoodleGroup(config, quantity);
                }
                return existing.addQuantity(quantity);
            });
        }
        return groups;
    }

    private SideGroupKey buildSideGroupKey(KitchenTask task, OrderItem orderItem) {
        SideDisplayParts parts = resolveSideDisplayParts(task, orderItem);
        List<String> keyDemands = parts.demands().stream()
            .sorted()
            .toList();
        return new SideGroupKey(parts.displayName(), keyDemands);
    }

    private SideDisplayParts resolveSideDisplayParts(KitchenTask task, OrderItem orderItem) {
        String itemName = fallback(task.item_name_snapshot_zh, task.item_name_snapshot_en, "Item");
        String special = normalize(task.special_instructions_snapshot);
        String displayName = itemName;
        List<String> demands = new ArrayList<>();

        if (special != null && special.contains("|")) {
            String[] parts = special.split("\\|", 2);
            String left = normalize(parts[0]);
            String right = parts.length > 1 ? normalize(parts[1]) : null;
            if (left != null) {
                displayName = left;
            }
            demands.addAll(splitDemandTokens(right));
        } else if (special != null && startsWithModifier(special)) {
            demands.addAll(splitDemandTokens(special));
        } else if (special != null) {
            displayName = special;
        }

        String itemNote = resolveItemNote(orderItem);
        if (itemNote != null) {
            demands.add("备注：" + itemNote);
        }

        List<String> simplified = simplifyGreenOptions(demands);
        return new SideDisplayParts(displayName, simplified);
    }

    private List<String> splitDemandTokens(String demandText) {
        if (demandText == null) {
            return List.of();
        }
        List<String> demands = new ArrayList<>();
        for (String token : demandText.trim().split("\\s+")) {
            String normalized = normalize(token);
            if (normalized != null) {
                demands.add(normalized);
            }
        }
        return demands;
    }

    private boolean startsWithModifier(String value) {
        for (String prefix : MODIFIER_PREFIXES) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private List<String> simplifyGreenOptions(List<String> lines) {
        boolean hasAddGreen = lines.stream().filter(Objects::nonNull).anyMatch(this::containsAddGreen);
        boolean hasRemoveGreen = lines.stream().filter(Objects::nonNull).anyMatch(this::containsRemoveGreen);
        if (hasAddGreen && hasRemoveGreen) {
            return lines.stream()
                .map(this::normalizeModifierSegments)
                .toList();
        }
        boolean simplifyAdd = hasAddOnion(lines) && hasAddCilantro(lines);
        boolean simplifyRemove = hasRemoveOnion(lines) && hasRemoveCilantro(lines);
        return lines.stream()
            .map(line -> simplifyGreenLine(line, simplifyAdd, simplifyRemove))
            .toList();
    }

    private String simplifyGreenLine(String line, boolean simplifyAdd, boolean simplifyRemove) {
        if (line == null || line.isBlank()) {
            return line;
        }
        String[] tokens = line.trim().split("\\s+");
        List<String> simplified = new ArrayList<>();
        for (String token : tokens) {
            if (simplifyAdd && isAddGreenToken(token)) {
                simplified.add("加青");
            } else if (simplifyRemove && isRemoveGreenToken(token)) {
                simplified.add("走青");
            } else {
                simplified.add(token);
            }
        }
        return normalizeModifierSegments(String.join(" ", simplified));
    }

    private String normalizeModifierSegments(String line) {
        if (line == null || line.isBlank()) {
            return line;
        }
        String[] segments = line.split("\\|", -1);
        List<String> normalizedSegments = new ArrayList<>();
        for (String segment : segments) {
            String normalized = normalizeModifierSegment(segment);
            if (normalized != null && !normalized.isBlank()) {
                normalizedSegments.add(normalized);
            }
        }
        return String.join(" | ", normalizedSegments);
    }

    private String normalizeModifierSegment(String segment) {
        if (segment == null) {
            return null;
        }
        String trimmed = segment.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        Map<String, ModifierCount> addOnCounts = new LinkedHashMap<>();
        List<String> orderedKeys = new ArrayList<>();
        LinkedHashSet<String> rawTokens = new LinkedHashSet<>();
        for (String token : trimmed.split("\\s+")) {
            ModifierToken modifierToken = parseAddOnModifierToken(token);
            if (modifierToken != null) {
                String key = modifierToken.normalizedName();
                if (!addOnCounts.containsKey(key)) {
                    orderedKeys.add("ADD:" + key);
                    addOnCounts.put(key, new ModifierCount(modifierToken.displayName(), 0));
                }
                ModifierCount current = addOnCounts.get(key);
                addOnCounts.put(key, new ModifierCount(current.displayName(), current.quantity() + modifierToken.quantity()));
                continue;
            }
            if (rawTokens.add(token)) {
                orderedKeys.add("RAW:" + token);
            }
        }

        List<String> result = new ArrayList<>();
        for (String key : orderedKeys) {
            if (key.startsWith("ADD:")) {
                ModifierCount count = addOnCounts.get(key.substring("ADD:".length()));
                if (count != null) {
                    result.add(count.quantity() > 1 ? "+" + count.displayName() + "x" + count.quantity() : "+" + count.displayName());
                }
            } else if (key.startsWith("RAW:")) {
                result.add(key.substring("RAW:".length()));
            }
        }
        return String.join(" ", result);
    }

    private String normalizeNoodleConfigSegment(String segment) {
        List<String> simplified = simplifyGreenOptions(List.of(KitchenNoodlePrintFormatter.normalizeModifierSegment(segment)));
        return simplified.isEmpty() ? KitchenNoodlePrintFormatter.normalizeModifierSegment(segment) : simplified.get(0);
    }

    private ModifierToken parseAddOnModifierToken(String token) {
        if (token == null || token.isBlank() || !token.startsWith("+")) {
            return null;
        }
        String body = token.substring(1).trim();
        if (body.isBlank()) {
            return null;
        }
        int quantity = 1;
        int markerIndex = findQuantityMarkerIndex(body);
        if (markerIndex > 0 && markerIndex < body.length() - 1) {
            String quantityText = body.substring(markerIndex + 1);
            if (quantityText.chars().allMatch(Character::isDigit)) {
                quantity = Integer.parseInt(quantityText);
                body = body.substring(0, markerIndex);
            }
        }
        if (body.isBlank()) {
            return null;
        }
        return new ModifierToken(body, body, quantity);
    }

    private int findQuantityMarkerIndex(String value) {
        int xIndex = value.lastIndexOf('x');
        int starIndex = value.lastIndexOf('*');
        return Math.max(xIndex, starIndex);
    }

    private boolean containsAddGreen(String line) {
        for (String token : line.split("\\s+")) {
            if (isAddGreenToken(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsRemoveGreen(String line) {
        for (String token : line.split("\\s+")) {
            if (isRemoveGreenToken(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAddGreenToken(String token) {
        return "+葱".equals(token)
            || "+香".equals(token)
            || "+香菜".equals(token)
            || "加葱".equals(token)
            || "加香".equals(token)
            || "加香菜".equals(token);
    }

    private boolean hasAddOnion(List<String> lines) {
        return lines.stream()
            .filter(Objects::nonNull)
            .flatMap(line -> List.of(line.split("\\s+")).stream())
            .anyMatch(this::isAddOnionToken);
    }

    private boolean hasAddCilantro(List<String> lines) {
        return lines.stream()
            .filter(Objects::nonNull)
            .flatMap(line -> List.of(line.split("\\s+")).stream())
            .anyMatch(this::isAddCilantroToken);
    }

    private boolean isAddOnionToken(String token) {
        return "+葱".equals(token) || "加葱".equals(token);
    }

    private boolean isAddCilantroToken(String token) {
        return "+香".equals(token)
            || "+香菜".equals(token)
            || "加香".equals(token)
            || "加香菜".equals(token);
    }

    private boolean isRemoveGreenToken(String token) {
        return "走葱".equals(token)
            || "走香".equals(token)
            || "走香菜".equals(token);
    }

    private boolean hasRemoveOnion(List<String> lines) {
        return lines.stream()
            .filter(Objects::nonNull)
            .flatMap(line -> List.of(line.split("\\s+")).stream())
            .anyMatch(this::isRemoveOnionToken);
    }

    private boolean hasRemoveCilantro(List<String> lines) {
        return lines.stream()
            .filter(Objects::nonNull)
            .flatMap(line -> List.of(line.split("\\s+")).stream())
            .anyMatch(this::isRemoveCilantroToken);
    }

    private boolean isRemoveOnionToken(String token) {
        return "走葱".equals(token);
    }

    private boolean isRemoveCilantroToken(String token) {
        return "走香".equals(token) || "走香菜".equals(token);
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

    private record SideDisplayParts(String displayName, List<String> demands) {
    }

    private record SideGroupKey(String displayName, List<String> demands) {
    }

    private record ModifierToken(String normalizedName, String displayName, int quantity) {
    }

    private record ModifierCount(String displayName, int quantity) {
    }

    private record NoodleGroup(KitchenNoodlePrintFormatter.NoodleConfig config, int quantity) {
        NoodleGroup addQuantity(int delta) {
            return new NoodleGroup(config, quantity + delta);
        }
    }

    private static class SideGroup {
        private final String displayName;
        private final List<String> demands;
        private int quantity;

        private SideGroup(String displayName, List<String> demands) {
            this.displayName = displayName;
            this.demands = demands;
        }

        private void addQuantity(int quantity) {
            this.quantity += quantity;
        }

        private List<String> toPrintLines() {
            List<String> lines = new ArrayList<>();
            lines.add(displayName + " x" + quantity);
            lines.addAll(demands);
            return lines;
        }
    }
}
