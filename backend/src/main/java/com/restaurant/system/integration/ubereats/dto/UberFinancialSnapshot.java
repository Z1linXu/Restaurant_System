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
        JsonNode rawCharges = payment.path("charges");
        ObjectNode charges = JsonNodeFactory.instance.objectNode();
        String currency = rawCharges.path("sub_total").path("currency_code").asText("");
        if (!currency.matches("[A-Z]{3}")) problems.add("CURRENCY_MISSING");
        for (String key : List.of("sub_total", "tax", "total", "total_fee")) {
            if (rawCharges.has(key)) charges.set(key, moneyNode(rawCharges.get(key)));
        }
        // Unsupported fees/promotions/accounting must not be silently treated as merchandise.
        if (!payment.isObject() || !rawCharges.isObject()) problems.add("CHARGES_MISSING");
        payment.fieldNames().forEachRemaining(key -> {
            if (!"charges".equals(key) && nonEmpty(payment.path(key)))
                problems.add("UNSUPPORTED_PAYMENT_DETAIL");
        });
        rawCharges.fieldNames().forEachRemaining(key -> {
            if (!Set.of("sub_total", "tax", "total", "total_fee").contains(key)
                    && nonEmpty(rawCharges.path(key))) problems.add("UNSUPPORTED_CHARGE_DETAIL");
        });
        for (String key : List.of("promotions", "discounts", "refunds", "adjustments", "tax_reporting")) {
            if (nonEmpty(order.path(key))) problems.add("UNSUPPORTED_FINANCIAL_ADJUSTMENT");
        }
        List<Line> lines = lines(order.path("cart").path("items"), 0, problems);
        if (lines.isEmpty()) problems.add("ITEM_AMOUNTS_MISSING");
        Set<String> instances = new HashSet<>();
        for (Line line : lines) {
            if (!line.instance_id().isEmpty() && !instances.add(line.instance_id()))
                problems.add("DUPLICATE_ITEM_INSTANCE");
        }
        Long subtotal = amount(charges, "sub_total", currency);
        Long tax = amount(charges, "tax", currency);
        Long total = amount(charges, "total", currency);
        Long fees = amount(charges, "total_fee", currency);
        if (subtotal == null || tax == null || total == null) problems.add("CHARGES_INCOMPLETE");
        if (charges.has("total_fee") && (fees == null || fees != 0)) problems.add("FEES_REQUIRE_REVIEW");
        BigDecimal weight = BigDecimal.ZERO;
        for (Line line : lines) {
            Long value = amount(line.price(), "total_price", currency);
            if (value == null) problems.add("ITEM_AMOUNT_INVALID");
            else weight = weight.add(BigDecimal.valueOf(value));
        }
        if (subtotal != null && weight.compareTo(BigDecimal.valueOf(subtotal)) != 0)
            problems.add("ITEM_SUBTOTAL_MISMATCH");
        if (subtotal != null && tax != null && total != null
                && BigDecimal.valueOf(subtotal).add(BigDecimal.valueOf(tax)).compareTo(BigDecimal.valueOf(total)) != 0)
            problems.add("MERCHANDISE_TOTAL_MISMATCH");
        if (weight.signum() == 0 && total != null && total != 0) problems.add("ZERO_WEIGHT_WITH_REVENUE");
        if (problems.isEmpty()) {
            List<Line> allocated = new ArrayList<>();
            BigDecimal cumulative = BigDecimal.ZERO;
            long assigned = 0;
            for (Line line : lines) {
                cumulative = cumulative.add(BigDecimal.valueOf(amount(line.price(), "total_price", currency)));
                long target = weight.signum() == 0 ? 0 : BigDecimal.valueOf(total).multiply(cumulative)
                        .divide(weight, 0, RoundingMode.HALF_UP).longValueExact();
                allocated.add(new Line(line.id(), line.instance_id(), line.quantity(), line.price(), line.modifiers(), target - assigned));
                assigned = target;
            }
            lines = List.copyOf(allocated);
        }
        return new UberFinancialSnapshot(1, text(order, "id"), text(order.path("store"), "id"),
                text(order, "placed_at"), currency, problems.isEmpty() ? total : null,
                problems.isEmpty() ? null : problems.get(0), charges, lines);
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
                if (node.path("price").has(key)) price.set(key, moneyNode(node.path("price").get(key)));
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
