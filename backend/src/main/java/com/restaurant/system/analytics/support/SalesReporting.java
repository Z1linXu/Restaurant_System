package com.restaurant.system.analytics.support;

import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared sales eligibility and frozen-line allocation; never reads current menu prices. */
public final class SalesReporting {
    private SalesReporting() {}

    public enum Group { SOUP_NOODLE, DRY_NOODLE, FRIED_NOODLE, DRINK, ALCOHOL, SIDE, FRIED, OTHER }

    private static final Map<Group, Set<String>> SKU_GROUPS = Map.of(
        Group.SOUP_NOODLE, Set.of("traditional_beef_noodle", "braised_beef_tendon_noodle", "braised_beef_noodle", "pickled_vegetable_beef_noodle", "vegetable_noodle"),
        Group.DRY_NOODLE, Set.of("zha_jiang_noodle", "dan_dan_noodle", "cold_noodle_shredded_chicken"),
        Group.FRIED_NOODLE, Set.of("beef_chow_mein", "chicken_chow_mein", "tomato_chow_mein", "vegetable_chow_mein"),
        Group.ALCOHOL, Set.of("soju", "shochu", "shochu_fruit", "sake", "lg_sake", "sm_sake", "sapporo", "tsingtao_beer"),
        Group.DRINK, Set.of("coke", "diet_coke", "seven_up", "canada_dry", "ice_tea", "chinese_herbal_tea"),
        Group.SIDE, Set.of("cucumber_salad", "edamame", "shredded_potato", "braised_beef_shank_salad", "tea_egg", "beef", "tendon"),
        Group.FRIED, Set.of("fried_egg", "fried_spring_rolls", "fried_steamed_buns", "fried_wontons", "tempura_shrimp")
    );

    public static boolean eligible(Order order) {
        return "completed".equalsIgnoreCase(order.status) && order.submitted_at != null
            && !order.kitchenMirror() && !"UBER_EATS".equalsIgnoreCase(order.external_source);
    }

    public static Group group(String categoryCode, String sku) {
        // Explicit stable identities resolve legacy mixed categories (notably Drinks).
        String normalizedSku = sku == null ? "" : sku.trim().toLowerCase(Locale.ROOT);
        for (var entry : SKU_GROUPS.entrySet()) {
            if (entry.getValue().contains(normalizedSku)) return entry.getKey();
        }
        String category = categoryCode == null ? "" : categoryCode.trim().toUpperCase(Locale.ROOT);
        return switch (category) {
            case "SOUP_NOODLE" -> Group.SOUP_NOODLE;
            case "DRY_NOODLE" -> Group.DRY_NOODLE;
            case "FRIED_NOODLE" -> Group.FRIED_NOODLE;
            case "NON_ALCOHOL_DRINK", "DRINK" -> Group.DRINK;
            case "ALCOHOL" -> Group.ALCOHOL;
            case "SIDE" -> Group.SIDE;
            case "FRIED" -> Group.FRIED;
            default -> Group.OTHER;
        };
    }

    public static boolean eligibleItem(OrderItem item) {
        // Cancellation retains the frozen line_amount for history, not sales.
        return !"cancelled".equalsIgnoreCase(item.status);
    }

    public record AllocatedLine(OrderItem item, BigDecimal revenue) {}

    /** Prorates the existing Sales total (including its existing tax/discount semantics).
     * line_amount already contains selected option charges. Cumulative cent rounding
     * preserves the exact order total, including orders with missing/zero-value lines. */
    public static List<AllocatedLine> allocate(List<Order> orders, List<OrderItem> items) {
        Map<Long, List<OrderItem>> byOrder = items.stream().filter(SalesReporting::eligibleItem).collect(Collectors.groupingBy(item -> item.order_id));
        List<AllocatedLine> result = new ArrayList<>();
        for (Order order : orders) {
            List<OrderItem> lines = byOrder.getOrDefault(order.id, List.of()).stream()
                .sorted(Comparator.comparing(item -> item.id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
            BigDecimal total = money(order.total_amount);
            BigDecimal weight = lines.stream().map(item -> positive(item.line_amount)).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal assigned = BigDecimal.ZERO;
            BigDecimal cumulative = BigDecimal.ZERO;
            for (OrderItem line : lines) {
                cumulative = cumulative.add(positive(line.line_amount));
                BigDecimal target = weight.signum() == 0 ? BigDecimal.ZERO
                    : total.multiply(cumulative).divide(weight, 2, RoundingMode.HALF_UP);
                result.add(new AllocatedLine(line, target.subtract(assigned)));
                assigned = target;
            }
            if (assigned.compareTo(total) != 0) result.add(new AllocatedLine(null, total.subtract(assigned)));
        }
        return result;
    }

    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal positive(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}
