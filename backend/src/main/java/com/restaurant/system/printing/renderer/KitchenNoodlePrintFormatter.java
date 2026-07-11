package com.restaurant.system.printing.renderer;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

final class KitchenNoodlePrintFormatter {

    private static final Set<String> NOODLE_CATEGORY_CODES = Set.of(
        "SOUP_NOODLE",
        "DRY_NOODLE",
        "FRIED_NOODLE",
        "NOODLE",
        "NOODLES"
    );
    private static final Set<String> MODIFIER_PREFIXES = Set.of("+", "走", "少", "不要", "无");

    private KitchenNoodlePrintFormatter() {
    }

    static boolean isNoodleTask(KitchenTask task, OrderItem item) {
        String categoryCode = stable(item == null ? null : item.category_code_snapshot);
        if (NOODLE_CATEGORY_CODES.contains(categoryCode)) {
            return true;
        }
        String stationCode = stable(task == null ? null : task.station_code);
        if ("NOODLE".equals(stationCode)) {
            return true;
        }

        String zh = normalize(task == null ? null : task.item_name_snapshot_zh);
        String en = normalize(task == null ? null : task.item_name_snapshot_en);
        if (zh == null && item != null) {
            zh = normalize(item.item_name_snapshot_zh);
        }
        if (en == null && item != null) {
            en = normalize(item.item_name_snapshot_en);
        }
        return (zh != null && zh.contains("面"))
            || (en != null && en.toLowerCase(Locale.ROOT).contains("noodle"));
    }

    static NoodleConfig buildConfig(KitchenTask task, OrderItem item, UnaryOperator<String> segmentNormalizer) {
        String itemName = fallback(
            task == null ? null : task.item_name_snapshot_zh,
            task == null ? null : task.item_name_snapshot_en,
            "Item"
        );
        String special = normalize(task == null ? null : task.special_instructions_snapshot);
        String display = special == null
            ? itemName
            : shouldUseSpecialAsPrimary(itemName, special)
                ? special
                : itemName + " | " + special;

        List<String> segments = new ArrayList<>();
        for (String segment : display.split("\\|", -1)) {
            String normalizedSegment = normalize(segment);
            if (normalizedSegment != null) {
                segments.add(applySegmentNormalizer(normalizedSegment, segmentNormalizer));
            }
        }
        String note = normalize(item == null ? null : item.notes);
        if (note != null) {
            segments.add("备注：" + note);
        }
        return new NoodleConfig(String.join(" | ", segments));
    }

    static String formatLine(NoodleConfig config, int quantity) {
        String display = config == null ? "Item" : config.displayText();
        if (quantity <= 1) {
            return display;
        }
        return "(" + display + ") ×" + quantity;
    }

    static NoodleGroupKey buildGroupKey(
        KitchenTask task,
        OrderItem item,
        List<OrderItemOption> options,
        NoodleConfig config
    ) {
        List<String> optionKeys = options == null
            ? List.of()
            : options.stream()
                .filter(option -> option != null)
                .sorted(KitchenNoodlePrintFormatter::compareOptions)
                .map(KitchenNoodlePrintFormatter::buildStableOptionKey)
                .toList();
        return new NoodleGroupKey(
            item == null ? null : item.menu_item_id,
            stable(item == null ? null : item.category_code_snapshot),
            stable(task == null ? null : task.station_code),
            config == null ? "" : stable(config.displayText()),
            stable(item == null ? null : item.notes),
            optionKeys
        );
    }

    static String normalizeModifierSegment(String segment) {
        String trimmed = normalize(segment);
        if (trimmed == null) {
            return "";
        }

        Map<String, ModifierCount> addOnCounts = new LinkedHashMap<>();
        List<String> orderedKeys = new ArrayList<>();
        for (String token : trimmed.split("\\s+")) {
            ModifierToken modifierToken = parseAddOnModifierToken(token);
            if (modifierToken == null) {
                orderedKeys.add("RAW:" + token);
                continue;
            }
            String key = modifierToken.normalizedName();
            if (!addOnCounts.containsKey(key)) {
                orderedKeys.add("ADD:" + key);
                addOnCounts.put(key, new ModifierCount(modifierToken.displayName(), 0));
            }
            ModifierCount current = addOnCounts.get(key);
            addOnCounts.put(key, new ModifierCount(current.displayName(), current.quantity() + modifierToken.quantity()));
        }

        List<String> result = new ArrayList<>();
        for (String key : orderedKeys) {
            if (key.startsWith("ADD:")) {
                ModifierCount count = addOnCounts.get(key.substring("ADD:".length()));
                if (count != null) {
                    result.add(count.quantity() > 1 ? "+" + count.displayName() + "×" + count.quantity() : "+" + count.displayName());
                }
                continue;
            }
            result.add(key.substring("RAW:".length()));
        }
        return String.join(" ", result);
    }

    private static int compareOptions(OrderItemOption left, OrderItemOption right) {
        int result = stable(left.option_group_snapshot).compareTo(stable(right.option_group_snapshot));
        if (result != 0) {
            return result;
        }
        result = stable(left.option_type_snapshot).compareTo(stable(right.option_type_snapshot));
        if (result != 0) {
            return result;
        }
        result = stable(left.option_code_snapshot).compareTo(stable(right.option_code_snapshot));
        if (result != 0) {
            return result;
        }
        result = Long.compare(left.option_id == null ? Long.MAX_VALUE : left.option_id, right.option_id == null ? Long.MAX_VALUE : right.option_id);
        if (result != 0) {
            return result;
        }
        return Long.compare(left.id == null ? Long.MAX_VALUE : left.id, right.id == null ? Long.MAX_VALUE : right.id);
    }

    private static String buildStableOptionKey(OrderItemOption option) {
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

    private static ModifierToken parseAddOnModifierToken(String token) {
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

    private static int findQuantityMarkerIndex(String value) {
        int xIndex = value.lastIndexOf('x');
        int multiplyIndex = value.lastIndexOf('×');
        int starIndex = value.lastIndexOf('*');
        return Math.max(Math.max(xIndex, multiplyIndex), starIndex);
    }

    private static boolean shouldUseSpecialAsPrimary(String itemName, String special) {
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

    private static String applySegmentNormalizer(String segment, UnaryOperator<String> segmentNormalizer) {
        if (segmentNormalizer == null) {
            return normalizeModifierSegment(segment);
        }
        String normalized = segmentNormalizer.apply(segment);
        return normalized == null || normalized.isBlank() ? segment : normalized.trim();
    }

    private static String fallback(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary.trim();
        }
        return fallback;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String stable(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    record NoodleConfig(String displayText) {
    }

    record NoodleGroupKey(
        Long menuItemId,
        String categoryCode,
        String stationCode,
        String displayText,
        String notes,
        List<String> optionKeys
    ) {
    }

    private record ModifierToken(String normalizedName, String displayName, int quantity) {
    }

    private record ModifierCount(String displayName, int quantity) {
    }
}
