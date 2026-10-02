package com.restaurant.system.integration.ubereats.dto;

import java.util.List;

/** Kitchen identity, minimal customer display name, and financial charges only. */
public record UberOrderSnapshot(
        String id,
        String store_id,
        String display_id,
        String current_state,
        String external_reference_id,
        String order_manager_client_id,
        String fulfillment_type,
        String placed_at,
        String estimated_ready_at,
        String notes,
        List<Item> items,
        List<String> issues,
        String customer_display_name,
        com.fasterxml.jackson.databind.JsonNode financial_charges) {
    public UberOrderSnapshot(
            String id,
            String store_id,
            String display_id,
            String current_state,
            String external_reference_id,
            String order_manager_client_id,
            String fulfillment_type,
            String placed_at,
            String estimated_ready_at,
            String notes,
            List<Item> items,
            List<String> issues) {
        this(
                id,
                store_id,
                display_id,
                current_state,
                external_reference_id,
                order_manager_client_id,
                fulfillment_type,
                placed_at,
                estimated_ready_at,
                notes,
                items,
                issues,
                "",
                null);
    }

    public record Item(
            String id,
            String external_data,
            String title,
            int quantity,
            boolean removed,
            String notes,
            List<Item> modifiers,
            List<String> issues) {}
}
