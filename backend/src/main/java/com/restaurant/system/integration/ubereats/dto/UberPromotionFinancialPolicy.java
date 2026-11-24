package com.restaurant.system.integration.ubereats.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.*;

/** Merchant merchandise including tax, after merchant promotions; never eater payment or payout. */
final class UberPromotionFinancialPolicy {
    static final String RULE = "MERCHANT_POST_PROMO_STRICT_V2";
    private static final List<String> CHARGES = List.of("sub_total", "tax", "total", "total_fee",
            "total_fee_tax", "sub_total_promo_applied", "tax_promo_applied", "total_promo_applied");
    private static final Set<String> PROMOTION = Set.of("external_promotion_id", "promotion_uuid",
            "promo_type", "promo_discount_value", "promo_discount_percentage", "promo_delivery_fee_value",
            "discount_items", "promo_funding_splits");
    private static final Set<String> TYPES = Set.of("FLATOFF", "PERCENTOFF", "FREEITEM_MINBASKET",
            "CATEGORY_DISCOUNT", "BOGO", "FREEDELIVERY", "MENU_ITEM_DISCOUNT");

    static final String ORDER_TOTAL_RULE = "MERCHANT_POST_PROMO_ORDER_TOTAL_V3";
    record Selection(int version, String currency, Long total, ObjectNode charges,
                     List<String> problems, List<String> allocationProblems) {}

    static Selection select(JsonNode raw, JsonNode promotions, BigDecimal itemWeight, boolean retained) {
        return select(raw,promotions,itemWeight,retained,false);
    }

    static Selection selectOrderTotal(JsonNode raw, JsonNode promotions, BigDecimal itemWeight, boolean retained) {
        return select(raw,promotions,itemWeight,retained,true);
    }

    private static Selection select(JsonNode raw, JsonNode promotions, BigDecimal itemWeight,
                                    boolean retained, boolean orderTotalPolicy) {
        boolean authoritative=orderTotalPolicy && raw.hasNonNull("total_promo_applied");
        var problems = new ArrayList<String>();
        var allocationProblems = new ArrayList<String>();
        ObjectNode charges = JsonNodeFactory.instance.objectNode();
        if (!raw.isObject()) problems.add("CHARGES_MISSING");
        String currency = raw.path("sub_total").path("currency_code").asText("");
        if (!currency.matches("[A-Z]{3}")) problems.add("CURRENCY_MISSING");
        raw.fieldNames().forEachRemaining(key -> {
            if (!CHARGES.contains(key) && nonEmpty(raw.path(key))) problems.add("UNSUPPORTED_CHARGE_DETAIL");
        });
        for (String key : CHARGES) {
            if (raw.hasNonNull(key)) {
                Long value = amount(raw.path(key), currency);
                if (value == null) problems.add("CHARGE_AMOUNT_INVALID");
                ObjectNode money = JsonNodeFactory.instance.objectNode();
                if (value != null) money.put("amount", value);
                money.put("currency_code", raw.path(key).path("currency_code").asText(""));
                charges.set(key, money);
            }
        }
        Long gross = amount(raw.path("sub_total"), currency);
        Long grossTax = amount(raw.path("tax"), currency);
        Long grossTotal = amount(raw.path("total"), currency);
        if (gross == null || grossTax == null || grossTotal == null) problems.add("CHARGES_INCOMPLETE");
        if (gross != null && itemWeight.compareTo(BigDecimal.valueOf(gross)) != 0)
            (authoritative ? allocationProblems : problems).add("ITEM_SUBTOTAL_MISMATCH");
        if (gross != null && grossTax != null && grossTotal != null
                && !BigDecimal.valueOf(gross).add(BigDecimal.valueOf(grossTax)).equals(BigDecimal.valueOf(grossTotal)))
            componentMismatch(BigDecimal.valueOf(grossTotal).subtract(BigDecimal.valueOf(gross).add(BigDecimal.valueOf(grossTax))),
                    authoritative,"MERCHANDISE_TOTAL_MISMATCH",problems,allocationProblems);
        for (String fee : List.of("total_fee", "total_fee_tax")) {
            if (raw.hasNonNull(fee) && !Long.valueOf(0).equals(amount(raw.path(fee), currency)))
                problems.add("FEES_REQUIRE_REVIEW");
        }

        Funding funding = funding(promotions, currency, problems);
        boolean promo = List.of("sub_total_promo_applied", "tax_promo_applied", "total_promo_applied")
                .stream().anyMatch(raw::hasNonNull);
        Long total = grossTotal;
        if (promo) {
            Long subtotal = amount(raw.path("sub_total_promo_applied"), currency);
            Long tax = amount(raw.path("tax_promo_applied"), currency);
            total = amount(raw.path("total_promo_applied"), currency);
            if (total == null) problems.add("PROMO_CHARGES_INCOMPLETE");
            if (subtotal == null || tax == null) (authoritative ? allocationProblems : problems).add("PROMO_COMPONENTS_INCOMPLETE");
            if (total != null && grossTotal != null && total > grossTotal) problems.add("PROMO_TOTAL_INVALID");
            if (subtotal != null && gross != null && subtotal > gross) problems.add("PROMO_SUBTOTAL_INVALID");
            if (tax != null && grossTax != null && tax > grossTax) problems.add("PROMO_TAX_INVALID");
            if (subtotal != null && tax != null && total != null
                    && BigDecimal.valueOf(subtotal).add(BigDecimal.valueOf(tax)).compareTo(BigDecimal.valueOf(total)) != 0)
                componentMismatch(BigDecimal.valueOf(total).subtract(BigDecimal.valueOf(subtotal).add(BigDecimal.valueOf(tax))),
                        authoritative,"PROMO_TOTAL_RECONCILIATION_REQUIRED",problems,allocationProblems);
            if (funding.complete && subtotal != null && gross != null
                    && BigDecimal.valueOf(gross).subtract(BigDecimal.valueOf(subtotal)).compareTo(funding.merchant) != 0)
                problems.add("PROMO_FUNDING_MISMATCH");
        } else if (nonEmpty(promotions) && (!funding.complete || funding.merchant.signum() != 0)) {
            problems.add("PROMO_CHARGES_MISSING");
        }
        if (itemWeight.signum() == 0 && total != null && total != 0)
            (authoritative ? allocationProblems : problems).add("ZERO_WEIGHT_WITH_REVENUE");
        if (authoritative) {
            // Order-level truth does not prove per-item net merchandise revenue.
            allocationProblems.add("PROMO_ITEM_ALLOCATION_UNVERIFIED");
            charges.put("_order_total_source","payment.charges.total_promo_applied");
            charges.put("_item_allocation_status","PENDING_RECONCILIATION");
            charges.put("_item_reconciliation_reason",allocationProblems.contains("PROMO_COMPONENT_ROUNDING_REVIEW")
                    ? "PROMO_COMPONENT_ROUNDING_REVIEW" : "PROMO_ITEM_ALLOCATION_UNVERIFIED");
            Long subtotal=amount(raw.path("sub_total_promo_applied"),currency);
            Long tax=amount(raw.path("tax_promo_applied"),currency);
            if(subtotal!=null && tax!=null && total!=null)
                charges.put("_component_delta_minor",BigDecimal.valueOf(total).subtract(BigDecimal.valueOf(subtotal).add(BigDecimal.valueOf(tax))));
            charges.put("_component_reconciliation_status",allocationProblems.contains("PROMO_COMPONENT_ROUNDING_REVIEW")
                    ? "ROUNDING_REVIEW" : subtotal==null||tax==null ? "INCOMPLETE" : "EXACT");
            charges.putArray("_allocation_review_reasons").addAll(allocationProblems.stream().map(JsonNodeFactory.instance::textNode).toList());
        }
        if (promo || nonEmpty(promotions) || retained) {
            charges.put("_analytics_rule", authoritative ? ORDER_TOTAL_RULE : RULE);
            charges.put("_allocation_basis", authoritative ? "UNCONFIRMED_ITEM_NET_AMOUNTS" : "GROSS_ITEM_WEIGHT_DERIVED");
            charges.put("_source_kind", retained ? "RETAINED_CHARGES" : "SAME_UBER_RESPONSE");
            charges.put("_funding_detail_complete", funding.complete);
            if (funding.complete) {
                charges.put("_merchant_funded_minor", funding.merchant.longValueExact());
                charges.put("_uber_funded_minor", funding.uber.longValueExact());
            }
        }
        return new Selection(authoritative ? 3 : promo || nonEmpty(promotions) || retained ? 2 : 1,
                currency,total,charges,List.copyOf(problems),List.copyOf(allocationProblems));
    }

    private static void componentMismatch(BigDecimal delta, boolean authoritative, String conflict,
                                          List<String> problems, List<String> allocationProblems) {
        if(authoritative && delta.abs().compareTo(BigDecimal.ONE)<=0)
            allocationProblems.add("PROMO_COMPONENT_ROUNDING_REVIEW");
        else problems.add(conflict);
    }

    private record Funding(boolean complete, BigDecimal merchant, BigDecimal uber) {}
    private static Funding funding(JsonNode node, String currency, List<String> problems) {
        if (!nonEmpty(node)) return new Funding(false, BigDecimal.ZERO, BigDecimal.ZERO);
        if (!node.isObject() || !node.path("promotions").isArray() || node.path("promotions").size() > 50) {
            problems.add("PROMOTION_SHAPE_INVALID");
            return new Funding(false, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        node.fieldNames().forEachRemaining(key -> {
            if (!key.equals("promotions") && nonEmpty(node.path(key))) problems.add("UNSUPPORTED_PROMOTION_DETAIL");
        });
        boolean complete = !node.path("promotions").isEmpty();
        BigDecimal merchant = BigDecimal.ZERO, uber = BigDecimal.ZERO;
        for (JsonNode promo : node.path("promotions")) {
            if (!promo.isObject() || !TYPES.contains(promo.path("promo_type").asText("")))
                problems.add("PROMOTION_TYPE_UNSUPPORTED");
            promo.fieldNames().forEachRemaining(key -> {
                if (!PROMOTION.contains(key) && nonEmpty(promo.path(key))) problems.add("UNSUPPORTED_PROMOTION_DETAIL");
            });
            for (String field : List.of("promo_discount_value", "promo_delivery_fee_value", "promo_discount_percentage")) {
                if (promo.hasNonNull(field) && (!promo.path(field).isIntegralNumber()
                        || !promo.path(field).canConvertToLong() || promo.path(field).longValue() < 0))
                    problems.add("PROMOTION_AMOUNT_INVALID");
            }
            if (promo.path("promo_discount_percentage").asLong(0) > 100) problems.add("PROMOTION_AMOUNT_INVALID");
            if (promo.path("promo_delivery_fee_value").asLong(0) != 0) problems.add("PROMOTION_FEES_REQUIRE_REVIEW");
            JsonNode discounts = promo.path("discount_items");
            if (nonEmpty(discounts) && (!discounts.isArray() || discounts.size() > 200))
                problems.add("PROMOTION_ITEMS_INVALID");
            if (discounts.isArray()) for (JsonNode item : discounts) {
                item.fieldNames().forEachRemaining(key -> {
                    if (!Set.of("external_id", "discounted_quantity", "discount_amount_applied").contains(key)
                            && nonEmpty(item.path(key))) problems.add("UNSUPPORTED_PROMOTION_ITEM_DETAIL");
                });
                if (!item.path("discounted_quantity").isIntegralNumber() || item.path("discounted_quantity").asInt(-1) < 0
                        || !item.path("discount_amount_applied").isIntegralNumber()
                        || !item.path("discount_amount_applied").canConvertToLong()) problems.add("PROMOTION_ITEMS_INVALID");
            }
            JsonNode splits = promo.path("promo_funding_splits");
            if (!splits.isArray() || splits.isEmpty()) { complete = false; continue; }
            if (splits.size() > 10) { problems.add("PROMOTION_FUNDING_INVALID"); continue; }
            BigDecimal sum = BigDecimal.ZERO;
            for (JsonNode split : splits) {
                split.fieldNames().forEachRemaining(key -> {
                    if (!Set.of("funding_source", "amount_paid").contains(key) && nonEmpty(split.path(key)))
                        problems.add("UNSUPPORTED_PROMOTION_FUNDING_DETAIL");
                });
                Long paid = amount(split.path("amount_paid"), currency);
                String source = split.path("funding_source").asText("");
                if (paid == null || !Set.of("uber", "organization").contains(source)) {
                    problems.add("PROMOTION_FUNDING_INVALID"); continue;
                }
                BigDecimal value = BigDecimal.valueOf(paid);
                sum = sum.add(value);
                if (source.equals("uber")) uber = uber.add(value); else merchant = merchant.add(value);
            }
            if (!promo.path("promo_discount_value").isIntegralNumber()
                    || sum.compareTo(promo.path("promo_discount_value").decimalValue()) != 0)
                problems.add("PROMOTION_FUNDING_MISMATCH");
        }
        if (merchant.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 || uber.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            problems.add("PROMOTION_FUNDING_INVALID"); complete = false;
        }
        return new Funding(complete, merchant, uber);
    }

    static Long amount(JsonNode node, String currency) {
        if (!node.isObject() || !currency.matches("[A-Z]{3}")
                || !currency.equals(node.path("currency_code").asText())) return null;
        var names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            if (!Set.of("amount", "currency_code", "formatted_amount").contains(key) && nonEmpty(node.path(key))) return null;
        }
        JsonNode value = node.path("amount");
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0 ? value.longValue() : null;
    }
    static boolean nonEmpty(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return false;
        if (node.isContainerNode()) return !node.isEmpty();
        if (node.isNumber()) return node.decimalValue().signum() != 0;
        return !node.isTextual() || !node.asText().isBlank();
    }
}
