package com.restaurant.system.printing.renderer;

import com.restaurant.system.common.pricing.TaxCalculator;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class FrontdeskReceiptRenderer implements ReceiptRenderer {

    private static final String COMBO_ROLE_MAIN = "main";
    private static final String COMBO_ROLE_SIDE = "combo_side";
    private static final String COMBO_ROLE_EGG = "combo_egg";
    private static final String OPTION_TYPE_ADDON = "addon";
    private static final String OPTION_TYPE_NOODLE_TYPE = "noodle_type";
    private static final String OPTION_TYPE_SIZE = "size";
    private static final String OPTION_TYPE_SPICY_LEVEL = "spicy_level";
    private static final String OPTION_GROUP_COMBO = "COMBO";
    private static final String OPTION_GROUP_COMBO_EGG = "COMBO_EGG";
    private static final String OPTION_GROUP_COMBO_SIDE = "COMBO_SIDE";
    private static final String OPTION_GROUP_COMBO_SIDE_REMOVE = "COMBO_SIDE_REMOVE";
    private static final String SOUP_NOODLE_CATEGORY = "SOUP_NOODLE";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public String getModuleCode() {
        return PrintModuleCode.FRONTDESK_RECEIPT;
    }

    @Override
    public String render(PrintRenderRequest request) {
        Order order = request.order;
        Map<Long, List<OrderItemOption>> optionsByItemId = (request.order_item_options == null ? List.<OrderItemOption>of() : request.order_item_options).stream()
            .collect(Collectors.groupingBy(option -> option.order_item_id));
        boolean updateTicket = Boolean.TRUE.equals(request.is_update_ticket);
        List<OrderItem> allItems = request.order_items == null ? List.of() : request.order_items.stream()
            .filter(item -> !"cancelled".equals(item.status))
            .sorted(Comparator.comparing(item -> item.id))
            .toList();

        BigDecimal subtotal = updateTicket ? calculateItemsSubtotal(allItems) : safeMoney(order.subtotal_amount);
        BigDecimal tax = updateTicket ? TaxCalculator.calculateTax(subtotal) : safeMoney(order.total_amount).subtract(subtotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = updateTicket ? TaxCalculator.calculateTotal(subtotal) : safeMoney(order.total_amount);

        StringBuilder builder = new StringBuilder();
        appendLargeLine(builder, resolveLargeDisplayLabel(order));
        if (updateTicket) {
            appendLargeLine(builder, "UPDATED");
            appendSizedLine(builder, "Added items only");
        }
        if ("pickup".equalsIgnoreCase(order.order_type) || "takeout".equalsIgnoreCase(order.order_type)) {
            appendSizedLine(builder, "Order Type: Takeout");
        }
        appendSizedLine(builder, "--------------------------------");

        Map<Integer, List<OrderItem>> comboSideItemsByGroup = allItems.stream()
            .filter(item -> COMBO_ROLE_SIDE.equals(item.combo_role))
            .filter(item -> item.combo_group_no != null)
            .collect(Collectors.groupingBy(item -> item.combo_group_no));

        List<OrderItem> items = allItems.stream()
            .filter(item -> shouldDisplayItem(item, optionsByItemId.getOrDefault(item.id, List.of())))
            .toList();

        for (OrderItem item : items) {
            List<OrderItemOption> options = optionsByItemId.getOrDefault(item.id, List.of());
            appendSizedLine(builder, buildItemLine(item, options));
            String noodleType = resolveNoodleTypeLabel(options);
            if (noodleType != null) {
                appendSizedLine(builder, noodleType);
            }
            String spicyLevel = resolveSpicyLevelLabel(options);
            if (spicyLevel != null) {
                appendSizedLine(builder, "辣度: " + spicyLevel);
            }
            List<String> chargeableLines = resolveChargeableOptionLines(options);
            for (String chargeableLine : chargeableLines) {
                appendSizedLine(builder, chargeableLine);
            }
            for (String comboSideLine : resolveComboSideLines(
                item,
                options,
                comboSideItemsByGroup.getOrDefault(item.combo_group_no, List.of()),
                optionsByItemId
            )) {
                appendSizedLine(builder, comboSideLine);
            }
            String itemNote = resolveItemNote(item);
            if (itemNote != null) {
                appendSizedLine(builder, "备注：" + itemNote);
            }
            appendSizedLine(builder, formatItemMoney(item));
            builder.append("\n");
        }

        appendSizedLine(builder, "--------------------------------");
        appendSizedLine(builder, "Subtotal: " + formatMoney(subtotal));
        appendSizedLine(builder, "Tax (" + TaxCalculator.TAX_RATE_LABEL + "): " + formatMoney(tax));
        appendSizedLine(builder, "Total: " + formatMoney(total));
        appendSizedLine(builder, "--------------------------------");
        if (order.submitted_at != null) {
            appendSizedLine(builder, "Submitted: " + order.submitted_at.format(TIME_FORMATTER));
        } else if (request.happened_at != null) {
            appendSizedLine(builder, "Printed: " + request.happened_at.format(TIME_FORMATTER));
        } else if (order.created_at != null) {
            appendSizedLine(builder, "Created: " + order.created_at.format(TIME_FORMATTER));
        }
        builder.append("\n");
        return builder.toString();
    }

    private void appendSizedLine(StringBuilder builder, String value) {
        builder.append(PrintMarkup.doubleHeight(value)).append("\n");
    }

    private void appendLargeLine(StringBuilder builder, String value) {
        builder.append(PrintMarkup.large(value)).append("\n");
    }

    private boolean shouldDisplayItem(OrderItem item, List<OrderItemOption> options) {
        if (COMBO_ROLE_SIDE.equals(item.combo_role) || COMBO_ROLE_EGG.equals(item.combo_role)) {
            return safeMoney(item.line_amount).compareTo(BigDecimal.ZERO) > 0;
        }
        return true;
    }

    private String buildItemLine(OrderItem item, List<OrderItemOption> options) {
        String baseName = resolveReceiptDisplayName(item);
        StringBuilder builder = new StringBuilder();
        int quantity = item.quantity == null ? 1 : item.quantity;
        boolean soupNoodle = isSoupNoodle(item);
        boolean combo = isComboItem(item, options);
        if (soupNoodle) {
            baseName = prependBowlSize(baseName, resolveSizeZhLabel(options));
        }
        if (soupNoodle && combo) {
            builder.append(quantity).append("* combo ");
        } else if (soupNoodle && quantity == 1) {
            // A single bowl reads more naturally without a redundant quantity prefix.
        } else {
            builder.append(quantity).append(" x ");
            if (combo) {
                builder.append("Combo ");
            }
        }
        builder.append(baseName);
        if (!soupNoodle) {
            String sizeEn = resolveSizeEnLabel(options);
            if (sizeEn != null) {
                builder.append(" ").append(sizeEn);
            }
        }
        return builder.toString();
    }

    private boolean isComboItem(OrderItem item, List<OrderItemOption> options) {
        if (options.stream().anyMatch(option -> isOptionGroup(option, OPTION_GROUP_COMBO))) {
            return true;
        }
        if (COMBO_ROLE_MAIN.equals(item.combo_role)) {
            return true;
        }
        return options.stream()
            .filter(option -> OPTION_TYPE_ADDON.equals(option.option_type_snapshot))
            .anyMatch(option -> containsComboLabel(option.option_name_snapshot_zh) || containsComboLabel(option.option_name_snapshot_en));
    }

    private boolean isSoupNoodle(OrderItem item) {
        return item.category_code_snapshot != null
            && SOUP_NOODLE_CATEGORY.equalsIgnoreCase(item.category_code_snapshot.trim());
    }

    private String resolveReceiptDisplayName(OrderItem item) {
        String baseName = firstPresent(item.item_name_snapshot_zh, item.item_name_snapshot_en);
        if (baseName != null && baseName.contains("传统牛肉面")) {
            return baseName.replace("传统牛肉面", "牛肉面");
        }
        return baseName == null ? "Item" : baseName;
    }

    private String prependBowlSize(String baseName, String sizeLabel) {
        if (sizeLabel == null || sizeLabel.isBlank()) {
            return baseName;
        }
        if (baseName.startsWith("中碗") || baseName.startsWith("大碗")) {
            return baseName;
        }
        return sizeLabel + baseName;
    }

    private String resolveSizeZhLabel(List<OrderItemOption> options) {
        return options.stream()
            .filter(option -> OPTION_TYPE_SIZE.equals(option.option_type_snapshot))
            .map(option -> mapSizeZh(option.option_name_snapshot_zh, option.option_name_snapshot_en))
            .filter(label -> label != null && !label.isBlank())
            .findFirst()
            .orElse(null);
    }

    private String resolveSizeEnLabel(List<OrderItemOption> options) {
        return options.stream()
            .filter(option -> OPTION_TYPE_SIZE.equals(option.option_type_snapshot))
            .map(option -> mapSizeEn(option.option_name_snapshot_zh, option.option_name_snapshot_en))
            .filter(label -> label != null && !label.isBlank())
            .findFirst()
            .orElse(null);
    }

    private String mapSizeZh(String optionZh, String optionEn) {
        String zh = optionZh == null ? "" : optionZh.trim();
        String en = optionEn == null ? "" : optionEn.trim();
        if ("大".equals(zh) || "大碗".equals(zh) || "large".equalsIgnoreCase(en)) {
            return "大碗";
        }
        if ("中".equals(zh)
            || "中碗".equals(zh)
            || "标准".equals(zh)
            || "标准碗".equals(zh)
            || "regular".equalsIgnoreCase(en)
            || "standard".equalsIgnoreCase(en)) {
            return "中碗";
        }
        return null;
    }

    private String mapSizeEn(String optionZh, String optionEn) {
        String zh = optionZh == null ? "" : optionZh.trim();
        String en = optionEn == null ? "" : optionEn.trim().toLowerCase();
        if (zh.contains("大") || en.contains("large")) {
            return "Large";
        }
        if (zh.contains("中") || zh.contains("标") || en.contains("regular") || en.contains("standard")) {
            return "Regular";
        }
        return null;
    }

    private String resolveNoodleTypeLabel(List<OrderItemOption> options) {
        return options.stream()
            .filter(option -> OPTION_TYPE_NOODLE_TYPE.equals(option.option_type_snapshot))
            .map(option -> firstPresent(option.option_name_snapshot_zh, option.option_name_snapshot_en))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private String resolveSpicyLevelLabel(List<OrderItemOption> options) {
        return options.stream()
            .filter(this::isSpicyLevelOption)
            .map(option -> firstPresent(option.option_name_snapshot_zh, option.option_name_snapshot_en))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private boolean isSpicyLevelOption(OrderItemOption option) {
        if (OPTION_TYPE_SPICY_LEVEL.equals(option.option_type_snapshot)) {
            return true;
        }
        if (isOptionGroup(option, "SPICY_LEVEL")) {
            return true;
        }
        String code = option.option_code_snapshot == null ? "" : option.option_code_snapshot.trim().toLowerCase();
        return code.contains("_spicy_level_") || code.startsWith("spicy_");
    }

    private List<String> resolveChargeableOptionLines(List<OrderItemOption> options) {
        return options.stream()
            .filter(option -> safeMoney(option.price_delta).compareTo(BigDecimal.ZERO) > 0)
            .filter(option -> !OPTION_TYPE_SIZE.equals(option.option_type_snapshot))
            .filter(option -> !isOptionGroup(option, OPTION_GROUP_COMBO))
            .filter(option -> !isOptionGroup(option, OPTION_GROUP_COMBO_EGG))
            .filter(option -> !isOptionGroup(option, OPTION_GROUP_COMBO_SIDE))
            .filter(option -> !isOptionGroup(option, OPTION_GROUP_COMBO_SIDE_REMOVE))
            .filter(option -> !containsComboLabel(option.option_name_snapshot_zh) && !containsComboLabel(option.option_name_snapshot_en))
            .map(this::formatChargeableOption)
            .toList();
    }

    private List<String> resolveComboSideLines(
        OrderItem item,
        List<OrderItemOption> options,
        List<OrderItem> comboSideItems,
        Map<Long, List<OrderItemOption>> optionsByItemId
    ) {
        if (!isComboItem(item, options)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        List<OrderItemOption> eggOptions = options.stream()
            .filter(option -> isOptionGroup(option, OPTION_GROUP_COMBO_EGG))
            .toList();
        if (eggOptions.isEmpty()) {
            addUniqueLine(lines, seen, "走蛋");
        } else {
            eggOptions.stream()
                .map(option -> cleanComboEggLabel(firstPresent(option.option_name_snapshot_zh, option.option_name_snapshot_en)))
                .forEach(label -> addUniqueLine(lines, seen, "鸡蛋: " + label));
        }

        List<OrderItemOption> sideOptions = options.stream()
            .filter(this::isComboSideOption)
            .toList();
        for (OrderItemOption sideOption : sideOptions) {
            addUniqueLine(lines, seen, "小菜: " + cleanComboSideLabel(firstPresent(sideOption.option_name_snapshot_zh, sideOption.option_name_snapshot_en)));
            options.stream()
                .filter(option -> isOptionGroup(option, OPTION_GROUP_COMBO_SIDE_REMOVE))
                .filter(option -> sideOption.option_id != null && sideOption.option_id.equals(option.parent_option_id_snapshot))
                .map(option -> firstPresent(option.option_name_snapshot_zh, option.option_name_snapshot_en))
                .filter(Objects::nonNull)
                .forEach(line -> addUniqueLine(lines, seen, line));
        }

        for (OrderItem sideItem : comboSideItems) {
            addUniqueLine(lines, seen, "小菜: " + cleanComboSideLabel(firstPresent(sideItem.item_name_snapshot_zh, sideItem.item_name_snapshot_en)));
            optionsByItemId.getOrDefault(sideItem.id, List.of()).stream()
                .filter(option -> safeMoney(option.price_delta).compareTo(BigDecimal.ZERO) == 0)
                .map(option -> firstPresent(option.option_name_snapshot_zh, option.option_name_snapshot_en))
                .filter(Objects::nonNull)
                .forEach(line -> addUniqueLine(lines, seen, line));
        }

        return lines;
    }

    private void addUniqueLine(List<String> lines, Set<String> seen, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        String normalized = line.trim().replaceAll("\\s+", " ").toLowerCase();
        if (seen.add(normalized)) {
            lines.add(line.trim());
        }
    }

    private String cleanComboSideLabel(String label) {
        if (label == null || label.isBlank()) {
            return "小菜";
        }
        String cleaned = label.trim();
        if (cleaned.startsWith("套餐")) {
            cleaned = cleaned.substring("套餐".length()).trim();
        }
        if (cleaned.toLowerCase().startsWith("combo ")) {
            cleaned = cleaned.substring("combo ".length()).trim();
        }
        return cleaned.isBlank() ? label.trim() : cleaned;
    }

    private String cleanComboEggLabel(String label) {
        if (label == null || label.isBlank()) {
            return "鸡蛋";
        }
        String cleaned = label.trim();
        if (cleaned.startsWith("套餐")) {
            cleaned = cleaned.substring("套餐".length()).trim();
        }
        if (cleaned.toLowerCase().startsWith("combo ")) {
            cleaned = cleaned.substring("combo ".length()).trim();
        }
        return cleaned.isBlank() ? label.trim() : cleaned;
    }

    private boolean isOptionGroup(OrderItemOption option, String group) {
        return option.option_group_snapshot != null && group.equalsIgnoreCase(option.option_group_snapshot);
    }

    private boolean isComboSideOption(OrderItemOption option) {
        if (isOptionGroup(option, OPTION_GROUP_COMBO_SIDE)) {
            return true;
        }
        if (option.option_code_snapshot == null) {
            return false;
        }
        String code = option.option_code_snapshot.trim().toLowerCase();
        return code.equals("combo_edamame")
            || code.equals("combo_shredded_potato")
            || code.equals("combo_cucumber_salad");
    }

    private String formatChargeableOption(OrderItemOption option) {
        String label = firstPresent(option.option_name_snapshot_zh, option.option_name_snapshot_en);
        int quantity = option.quantity == null ? 1 : option.quantity;
        return label + " x" + quantity;
    }

    private String formatItemMoney(OrderItem item) {
        BigDecimal unitPrice = safeMoney(item.unit_price);
        BigDecimal lineAmount = safeMoney(item.line_amount);
        int quantity = item.quantity == null ? 1 : item.quantity;
        if (quantity > 1 && unitPrice.compareTo(lineAmount) != 0) {
            return formatMoney(unitPrice) + " x" + quantity + " = " + formatMoney(lineAmount);
        }
        return formatMoney(lineAmount);
    }

    private boolean containsComboLabel(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.contains("套餐") || normalized.contains("combo");
    }

    private String firstPresent(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary.trim();
        }
        return null;
    }

    private String resolveItemNote(OrderItem item) {
        if (item.notes == null || item.notes.isBlank()) {
            return null;
        }
        return item.notes.trim();
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateItemsSubtotal(List<OrderItem> items) {
        return items.stream()
            .map(item -> safeMoney(item.line_amount))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal value) {
        return "$" + safeMoney(value);
    }

    private String resolveDisplayLabel(Order order) {
        if (order.table_no != null && !order.table_no.isBlank()) {
            return PrintTableDisplayFormatter.formatSplitTableLabel(order.table_no);
        }
        if (order.pickup_no != null && !order.pickup_no.isBlank()) {
            return order.pickup_no;
        }
        return "Walk-in";
    }

    private String resolveLargeDisplayLabel(Order order) {
        if (order.table_no != null && !order.table_no.isBlank()) {
            return "桌号: " + PrintTableDisplayFormatter.formatSplitTableLabel(order.table_no);
        }
        if (order.pickup_no != null && !order.pickup_no.isBlank()) {
            return "取餐号: " + order.pickup_no;
        }
        return "桌号: Walk-in";
    }

    private String center(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        int padding = (width - value.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + value;
    }
}
