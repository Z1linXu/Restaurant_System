package com.restaurant.system.integration.ubereats.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Same-response financial evidence. Never reads a local price or invents missing Uber money. */
public record UberFinancialSnapshot(
        int version, String order_id, String store_id, String placed_at, String currency,
        Long revenue_minor, String blocked_reason, JsonNode charges, List<Line> items) {
    public record Line(String id, String instance_id, int quantity, JsonNode price,
                       List<Line> modifiers, Long revenue_minor) {}

    public static UberFinancialSnapshot capture(JsonNode order) {
        List<String> problems = new ArrayList<>();
        JsonNode payment = order.path("payment");
        if (!payment.isObject()) problems.add("CHARGES_MISSING");
        payment.fieldNames().forEachRemaining(key -> {
            if (!Set.of("charges", "promotions").contains(key) && nonEmpty(payment.path(key)))
                problems.add(key.equals("refunds") ? "REFUNDS_REQUIRE_REVIEW" : "UNSUPPORTED_PAYMENT_DETAIL");
        });
        for (String key : List.of("promotions", "discounts", "refunds", "adjustments", "tax_reporting")) {
            if (nonEmpty(order.path(key))) problems.add(key.equals("refunds") ? "REFUNDS_REQUIRE_REVIEW" : "UNSUPPORTED_FINANCIAL_ADJUSTMENT");
        }
        List<Line> lines = lines(order.path("cart").path("items"), 0, problems);
        return reconcile(text(order, "id"), text(order.path("store"), "id"), text(order, "placed_at"),
                lines, payment.path("charges"), payment.path("promotions"), false, true, problems);
    }

    /** Pure preview only. Original v1 remains immutable; activation requires a correction journal entry. */
    public static UberFinancialSnapshot reconcileRetained(UberFinancialSnapshot original, JsonNode charges) {
        return reconcileRetained(original,charges,false);
    }

    public static UberFinancialSnapshot reconcileRetainedOrderTotal(UberFinancialSnapshot original, JsonNode charges) {
        return reconcileRetained(original,charges,true);
    }

    private static UberFinancialSnapshot reconcileRetained(UberFinancialSnapshot original, JsonNode charges, boolean orderTotalPolicy) {
        List<String> problems = new ArrayList<>();
        if(orderTotalPolicy && !"UNSUPPORTED_PAYMENT_DETAIL".equals(original.blocked_reason()))
            problems.add("ORIGINAL_REASON_NOT_APPROVED");
        if (original.version() != 1 || original.revenue_minor() != null || original.blocked_reason() == null)
            problems.add("ORIGINAL_NOT_BLOCKED_V1");
        for (String key : List.of("sub_total", "tax", "total")) {
            if (!Objects.equals(amount(original.charges(), key, original.currency()), amount(charges, key, original.currency())))
                problems.add("SOURCE_BASELINE_CHANGED");
        }
        if (!charges.hasNonNull("total_promo_applied")) problems.add("PROMO_CHARGES_MISSING");
        return reconcile(original.order_id(), original.store_id(), original.placed_at(), original.items(),
                charges, JsonNodeFactory.instance.nullNode(), true, orderTotalPolicy, problems);
    }

    private static UberFinancialSnapshot reconcile(String orderId, String storeId, String placedAt, List<Line> lines,
            JsonNode rawCharges, JsonNode promotions, boolean retained, boolean orderTotalPolicy, List<String> problems) {
        if (lines == null || lines.isEmpty() || lines.size() > 200) {
            problems.add("ITEM_AMOUNTS_MISSING"); lines = List.of();
        }
        Set<String> instances = new HashSet<>();
        String currency = rawCharges.path("sub_total").path("currency_code").asText("");
        BigDecimal weight = BigDecimal.ZERO;
        for (Line line : lines) {
            if (line.id() == null || line.id().isBlank() || line.quantity() < 1 || line.quantity() > 100)
                problems.add("FINANCIAL_ITEM_IDENTITY_INVALID");
            if (line.instance_id() != null && !line.instance_id().isEmpty() && !instances.add(line.instance_id()))
                problems.add("DUPLICATE_ITEM_INSTANCE");
            Long value = amount(line.price(), "total_price", currency);
            if (value == null) {
                if (!(orderTotalPolicy && rawCharges.hasNonNull("total_promo_applied") && !line.price().hasNonNull("total_price")))
                    problems.add("ITEM_AMOUNT_INVALID");
            }
            else weight = weight.add(BigDecimal.valueOf(value));
        }
        var selected = orderTotalPolicy ? UberPromotionFinancialPolicy.selectOrderTotal(rawCharges,promotions,weight,retained)
                : UberPromotionFinancialPolicy.select(rawCharges, promotions, weight, retained);
        if(selected.version()==3) problems.removeIf(problem -> Set.of("ITEM_AMOUNTS_MISSING",
                "FINANCIAL_ITEMS_SHAPE_INVALID","FINANCIAL_ITEM_IDENTITY_INVALID","DUPLICATE_ITEM_INSTANCE").contains(problem));
        problems.addAll(selected.problems());
        List<Line> allocated = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        long assigned = 0;
        for (Line line : lines) {
            Long revenue = null;
            if (problems.isEmpty() && selected.allocationProblems().isEmpty()) {
                cumulative = cumulative.add(BigDecimal.valueOf(amount(line.price(), "total_price", currency)));
                long target = weight.signum() == 0 ? 0 : BigDecimal.valueOf(selected.total()).multiply(cumulative)
                        .divide(weight, 0, RoundingMode.HALF_UP).longValueExact();
                revenue = target - assigned; assigned = target;
            }
            allocated.add(new Line(line.id(), line.instance_id(), line.quantity(), line.price(), line.modifiers(), revenue));
        }
        return new UberFinancialSnapshot(selected.version(), orderId, storeId, placedAt, currency,
                problems.isEmpty() ? selected.total() : null, problems.isEmpty() ? null : problems.get(0),
                selected.charges(), List.copyOf(allocated));
    }

    private static List<Line> lines(JsonNode nodes, int depth, List<String> problems) {
        if (!nodes.isArray() || nodes.size() > 200 || depth > 8) {
            problems.add("FINANCIAL_ITEMS_SHAPE_INVALID");
            return List.of();
        }
        List<Line> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            ObjectNode price = JsonNodeFactory.instance.objectNode();
            for (String key : List.of("unit_price", "total_price", "base_unit_price", "base_total_price"))
                if (node.path("price").has(key)) {
                    JsonNode money = node.path("price").get(key);
                    money.fieldNames().forEachRemaining(field -> {
                        if (!Set.of("amount", "currency_code", "formatted_amount").contains(field) && nonEmpty(money.path(field)))
                            problems.add("UNSUPPORTED_ITEM_MONEY_DETAIL");
                    });
                    price.set(key, moneyNode(money));
                }
            node.path("price").fieldNames().forEachRemaining(key -> {
                if (!Set.of("unit_price", "total_price", "base_unit_price", "base_total_price").contains(key)
                        && nonEmpty(node.path("price").path(key))) problems.add("UNSUPPORTED_ITEM_PRICE_DETAIL");
            });
            int quantity = node.path("quantity").asInt(0);
            if (!node.path("quantity").isIntegralNumber() || quantity < 1 || quantity > 100
                    || text(node, "id").isEmpty()) problems.add("FINANCIAL_ITEM_IDENTITY_INVALID");
            List<Line> modifiers = new ArrayList<>();
            JsonNode groups = node.path("selected_modifier_groups");
            if (groups.isArray()) for (JsonNode group : groups) {
                if (group.path("selected_items").isArray()) modifiers.addAll(lines(group.path("selected_items"), depth + 1, problems));
                // Removed options have no sale value and are preserved in the existing kitchen snapshot.
            }
            result.add(new Line(text(node, "id"), text(node, "instance_id"), quantity, price, List.copyOf(modifiers), null));
        }
        return List.copyOf(result);
    }

    private static JsonNode moneyNode(JsonNode node) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        if (node.path("amount").isIntegralNumber() && node.path("amount").canConvertToLong())
            result.put("amount", node.path("amount").longValue());
        if (node.path("currency_code").isTextual()) result.put("currency_code", node.path("currency_code").asText());
        return result;
    }

    private static Long amount(JsonNode node, String key, String currency) {
        if (!currency.equals(node.path(key).path("currency_code").asText())) return null;
        JsonNode value = node.path(key).path("amount");
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0 ? value.longValue() : null;
    }

    private static boolean nonEmpty(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) return false;
        if (node.isContainerNode()) return !node.isEmpty();
        if (node.isNumber()) return node.decimalValue().signum() != 0;
        return !node.isTextual() || !node.asText().isBlank();
    }

    private static String text(JsonNode node, String key) {
        return node.path(key).isTextual() && node.path(key).asText().length() <= 255 ? node.path(key).asText() : "";
    }
}
