package com.restaurant.system.allo;

import com.restaurant.system.common.pricing.TaxCalculator;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.order.entity.*;
import java.math.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Determined order money, shared by adjustments, receipts and live reporting. */
public final class AlloMoney {
    private AlloMoney() {}
    public record Amounts(BigDecimal gross, BigDecimal discount, BigDecimal net, BigDecimal tax,
                          BigDecimal total, BigDecimal collected) {}
    public static BigDecimal cents(BigDecimal n) { return (n == null ? BigDecimal.ZERO : n).setScale(2, RoundingMode.HALF_UP); }
    public static int effectiveQuantity(OrderItem i) {
        return "cancelled".equals(i.status) ? 0 : Math.max(0, i.quantity - (i.voided_quantity == null ? 0 : i.voided_quantity));
    }
    public static BigDecimal effectiveLine(OrderItem i) {
        if (effectiveQuantity(i) == 0) return cents(BigDecimal.ZERO);
        // Retain deterministic cent allocation even for historical non-divisible totals.
        return cents(i.line_amount).multiply(BigDecimal.valueOf(effectiveQuantity(i)))
            .divide(BigDecimal.valueOf(i.quantity), 2, RoundingMode.HALF_UP);
    }
    public static int originalOptionQuantity(OrderItem item, OrderItemOption option) {
        return Math.multiplyExact(item.quantity, perServing(option));
    }

    public static int effectiveOptionQuantity(OrderItem item, OrderItemOption option) {
        return Math.max(0, Math.multiplyExact(effectiveQuantity(item), perServing(option)) - option.voided_quantity);
    }

    public static boolean canVoidOption(OrderItemOption option) {
        return ("addon".equalsIgnoreCase(option.option_type_snapshot) || "ADD_ON".equals(option.option_group_snapshot))
            && option.price_delta != null && option.price_delta.signum() >= 0;
    }

    public static int printedOptionQuantity(OrderItemOption option, int servings) {
        return option.effective_total_quantity != null
            ? option.effective_total_quantity : Math.multiplyExact(servings, perServing(option));
    }

    private static int perServing(OrderItemOption option) {
        return option.quantity == null ? 1 : option.quantity;
    }

    public static Map<Long, List<OrderItemOption>> optionsByItem(List<OrderItemOption> options) {
        return options.stream().collect(Collectors.groupingBy(option -> option.order_item_id));
    }

    public static BigDecimal effectiveLine(OrderItem item, List<OrderItemOption> options) {
        BigDecimal result = effectiveLine(item);
        for (var option : options) {
            if (option.voided_quantity == 0) continue;
            int removed = Math.min(option.voided_quantity, Math.multiplyExact(effectiveQuantity(item), perServing(option)));
            result = result.subtract(cents(option.price_delta).multiply(BigDecimal.valueOf(removed)));
        }
        // Old non-divisible snapshot totals can leave a rounding cent at the boundary.
        return result.max(cents(BigDecimal.ZERO));
    }
    public static void validateDiscount(String type, BigDecimal value) {
        if (!Set.of("PERCENT", "AMOUNT").contains(type == null ? "" : type) || value == null
            || value.scale() > 2 || value.signum() < 0 || value.compareTo(new BigDecimal("999999.99")) > 0
            || "PERCENT".equals(type) && value.compareTo(new BigDecimal("100")) > 0)
            throw new BusinessException("DISCOUNT_INVALID");
    }
    public static Amounts calculate(BigDecimal gross, String type, BigDecimal value, boolean completed) {
        gross = cents(gross);
        BigDecimal discount = BigDecimal.ZERO;
        if (type != null) {
            validateDiscount(type,value);
            discount = "PERCENT".equals(type) ? gross.multiply(value).movePointLeft(2) : value;
        }
        discount = cents(discount).min(gross);
        BigDecimal net = gross.subtract(discount);
        BigDecimal tax = TaxCalculator.calculateTax(net);
        BigDecimal total = net.add(tax);
        return new Amounts(gross,discount,net,tax,total,completed ? total : cents(BigDecimal.ZERO));
    }
    public static Amounts determined(Order o) {
        BigDecimal gross=cents(o.subtotal_amount), discount=cents(o.discount_amount), total=cents(o.total_amount);
        BigDecimal net=gross.subtract(discount);
        return new Amounts(gross,discount,net,o.tax_amount == null ? total.subtract(net) : cents(o.tax_amount),total,
            "completed".equals(o.status) ? total : cents(BigDecimal.ZERO));
    }
    public static List<OrderItem> effectiveItems(List<OrderItem> items) {
        return effectiveItems(items, List.of());
    }

    public static List<OrderItem> effectiveItems(List<OrderItem> items, List<OrderItemOption> options) {
        var byItem = optionsByItem(options);
        return items.stream().filter(i -> effectiveQuantity(i)>0).map(i -> {
            var copy = AlloCopies.copy(i, new OrderItem());
            copy.quantity = effectiveQuantity(i);
            copy.line_amount = effectiveLine(i, byItem.getOrDefault(i.id, List.of()));
            copy.voided_quantity = 0;
            return copy;
        }).toList();
    }

    public static List<OrderItemOption> effectiveOptions(List<OrderItem> items, List<OrderItemOption> options) {
        var byId = items.stream().collect(Collectors.toMap(i -> i.id, Function.identity()));
        var effective = new ArrayList<OrderItemOption>();
        for (var option : options) {
            var item = byId.get(option.order_item_id);
            if (item == null || effectiveQuantity(item) == 0) continue;
            if (option.voided_quantity == 0) {
                effective.add(option);
                continue;
            }
            int quantity = effectiveOptionQuantity(item, option);
            if (quantity == 0) continue;
            var copy = AlloCopies.copy(option, new OrderItemOption());
            int servings = effectiveQuantity(item);
            copy.quantity = Math.max(1, quantity / servings);
            if (quantity % servings != 0) copy.effective_total_quantity = quantity;
            effective.add(copy);
        }
        return effective;
    }
}
