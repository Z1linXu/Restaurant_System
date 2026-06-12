package com.restaurant.system.printing.renderer;

import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        BigDecimal subtotal = safeMoney(order.subtotal_amount);
        BigDecimal total = safeMoney(order.total_amount);
        BigDecimal tax = total.subtract(subtotal).setScale(2, RoundingMode.HALF_UP);

        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append(center("FRONTDESK RECEIPT", 32)).append("\n");
        builder.append(PrintMarkup.doubleHeight(resolveLargeDisplayLabel(order))).append("\n");
        if ("pickup".equalsIgnoreCase(order.order_type) || "takeout".equalsIgnoreCase(order.order_type)) {
            builder.append("Order Type: Takeout").append("\n");
        }
        builder.append("--------------------------------\n");

        List<OrderItem> items = request.order_items == null ? List.of() : request.order_items.stream()
            .filter(item -> !"cancelled".equals(item.status))
            .filter(item -> shouldDisplayItem(item, optionsByItemId.getOrDefault(item.id, List.of())))
            .sorted(Comparator.comparing(item -> item.id))
            .toList();

        for (OrderItem item : items) {
            List<OrderItemOption> options = optionsByItemId.getOrDefault(item.id, List.of());
            builder.append(buildDisplayName(item, options))
                .append(" x")
                .append(item.quantity == null ? 1 : item.quantity)
                .append("\n");
            String noodleType = resolveNoodleTypeLabel(options);
            if (noodleType != null) {
                builder.append(noodleType).append("\n");
            }
            List<String> chargeableLines = resolveChargeableOptionLines(options);
            for (String chargeableLine : chargeableLines) {
                builder.append(chargeableLine).append("\n");
            }
            builder.append(formatItemMoney(item)).append("\n\n");
        }

        builder.append("--------------------------------\n");
        builder.append("Subtotal: ").append(formatMoney(subtotal)).append("\n");
        builder.append("Tax: ").append(formatMoney(tax)).append("\n");
        builder.append("Total: ").append(formatMoney(total)).append("\n");
        builder.append("--------------------------------\n");
        if (order.submitted_at != null) {
            builder.append("Submitted: ").append(order.submitted_at.format(TIME_FORMATTER)).append("\n");
        } else if (request.happened_at != null) {
            builder.append("Printed: ").append(request.happened_at.format(TIME_FORMATTER)).append("\n");
        } else if (order.created_at != null) {
            builder.append("Created: ").append(order.created_at.format(TIME_FORMATTER)).append("\n");
        }
        builder.append("\n");
        return builder.toString();
    }

    private boolean shouldDisplayItem(OrderItem item, List<OrderItemOption> options) {
        if (COMBO_ROLE_SIDE.equals(item.combo_role) || COMBO_ROLE_EGG.equals(item.combo_role)) {
            return safeMoney(item.line_amount).compareTo(BigDecimal.ZERO) > 0;
        }
        return true;
    }

    private String buildDisplayName(OrderItem item, List<OrderItemOption> options) {
        String baseName = item.item_name_snapshot_zh == null ? item.item_name_snapshot_en : item.item_name_snapshot_zh;
        StringBuilder builder = new StringBuilder();
        String sizeZh = resolveSizeZhLabel(options);
        if (sizeZh != null) {
            builder.append(sizeZh);
        }
        builder.append(baseName == null ? "Item" : baseName);
        if (isComboItem(item, options)) {
            builder.append(" Combo");
        }
        String sizeEn = resolveSizeEnLabel(options);
        if (sizeEn != null) {
            builder.append(" ").append(sizeEn);
        }
        return builder.toString();
    }

    private boolean isComboItem(OrderItem item, List<OrderItemOption> options) {
        if (COMBO_ROLE_MAIN.equals(item.combo_role)) {
            return true;
        }
        return options.stream()
            .filter(option -> OPTION_TYPE_ADDON.equals(option.option_type_snapshot))
            .anyMatch(option -> containsComboLabel(option.option_name_snapshot_zh) || containsComboLabel(option.option_name_snapshot_en));
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
        String en = optionEn == null ? "" : optionEn.trim().toLowerCase();
        if (zh.contains("大") || en.contains("large")) {
            return "大碗";
        }
        if (zh.contains("中") || zh.contains("标") || en.contains("regular") || en.contains("standard")) {
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

    private List<String> resolveChargeableOptionLines(List<OrderItemOption> options) {
        return options.stream()
            .filter(option -> safeMoney(option.price_delta).compareTo(BigDecimal.ZERO) > 0)
            .filter(option -> !OPTION_TYPE_SIZE.equals(option.option_type_snapshot))
            .filter(option -> !containsComboLabel(option.option_name_snapshot_zh) && !containsComboLabel(option.option_name_snapshot_en))
            .map(this::formatChargeableOption)
            .toList();
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

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal value) {
        return "$" + safeMoney(value);
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

    private String resolveLargeDisplayLabel(Order order) {
        if (order.table_no != null && !order.table_no.isBlank()) {
            return "桌号: " + order.table_no;
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
