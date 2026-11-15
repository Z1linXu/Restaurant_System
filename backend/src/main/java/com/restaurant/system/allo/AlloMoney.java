package com.restaurant.system.allo;

import com.restaurant.system.common.pricing.TaxCalculator;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.order.entity.*;
import java.math.*;
import java.util.*;

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
        return items.stream().filter(i -> effectiveQuantity(i)>0).map(i -> {
            OrderItem copy=new OrderItem(); com.restaurant.system.allo.AlloCopies.copy(i,copy);
            copy.quantity=effectiveQuantity(i); copy.line_amount=effectiveLine(i); copy.voided_quantity=0; return copy;
        }).toList();
    }
}
