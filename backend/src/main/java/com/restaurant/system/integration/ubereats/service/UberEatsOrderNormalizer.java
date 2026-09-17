package com.restaurant.system.integration.ubereats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.integration.ubereats.dto.UberOrderSnapshot;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class UberEatsOrderNormalizer {
    public UberOrderSnapshot normalize(JsonNode order) {
        if (order == null || !order.isObject())
            throw new IllegalArgumentException("UBER_ORDER_MALFORMED");
        List<String> issues = new ArrayList<>();
        if (nonEmpty(order.path("cart").path("fulfillment_issues")))
            issues.add("FULFILLMENT_ISSUES_REQUIRE_REVIEW");
        // Structured allergy requests are blocked until their full contract is supported.
        var items = items(order.path("cart").path("items"), false, 0, issues);
        if (items.isEmpty()) issues.add("ORDER_EMPTY");
        String type = text(order, "type", 80);
        if (!Set.of("DELIVERY_BY_UBER", "DELIVERY_BY_RESTAURANT", "PICK_UP").contains(type))
            issues.add("UNSUPPORTED_FULFILLMENT_TYPE");
        return new UberOrderSnapshot(
                text(order, "id", 255),
                text(order.path("store"), "id", 255),
                text(order, "display_id", 80),
                text(order, "current_state", 80),
                text(order, "external_reference_id", 255),
                text(order, "order_manager_client_id", 255),
                type,
                text(order, "placed_at", 80),
                text(order, "estimated_ready_for_pickup_at", 80),
                text(order.path("cart"), "special_instructions", 2000),
                items,
                issues);
    }

    private List<UberOrderSnapshot.Item> items(
            JsonNode nodes, boolean removed, int depth, List<String> parentIssues) {
        if (nodes.isMissingNode() || nodes.isNull()) return List.of();
        if (!nodes.isArray() || depth > 8 || nodes.size() > 200) {
            parentIssues.add("UNSUPPORTED_CART_SHAPE");
            return List.of();
        }
        List<UberOrderSnapshot.Item> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            List<String> issues = new ArrayList<>();
            if (nonEmpty(node.path("special_requests")))
                issues.add("STRUCTURED_SPECIAL_REQUESTS_REQUIRE_REVIEW");
            if (nonEmpty(node.path("fulfillment_action")))
                issues.add("FULFILLMENT_ACTION_REQUIRES_REVIEW");
            if (!node.path("quantity").isIntegralNumber()) issues.add("QUANTITY_REQUIRED");
            int quantity = removed ? 1 : node.path("quantity").asInt();
            if (quantity < 1 || quantity > 100) issues.add("QUANTITY_OUT_OF_RANGE");
            List<UberOrderSnapshot.Item> modifiers = new ArrayList<>();
            JsonNode groups = node.path("selected_modifier_groups");
            if (!groups.isMissingNode() && !groups.isNull() && !groups.isArray())
                issues.add("UNSUPPORTED_MODIFIER_GROUP");
            if (groups.isArray())
                for (JsonNode group : groups) {
                    modifiers.addAll(items(group.path("selected_items"), false, depth + 1, issues));
                    modifiers.addAll(items(group.path("removed_items"), true, depth + 1, issues));
                }
            result.add(
                    new UberOrderSnapshot.Item(
                            text(node, "id", 255),
                            text(node, "external_data", 255),
                            text(node, "title", 300),
                            quantity,
                            removed,
                            text(node, "special_instructions", 2000),
                            modifiers,
                            issues));
        }
        return result;
    }

    private boolean nonEmpty(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) return false;
        if (node.isContainerNode()) return !node.isEmpty();
        return !node.isTextual() || !node.asText().isBlank();
    }

    private String text(JsonNode node, String key, int max) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) return "";
        if (!value.isTextual() || value.asText().length() > max)
            throw new IllegalArgumentException("UBER_ORDER_FIELD_INVALID");
        return value.asText();
    }
}
