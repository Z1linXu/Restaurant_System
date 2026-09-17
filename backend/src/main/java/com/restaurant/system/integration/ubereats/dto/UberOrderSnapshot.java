package com.restaurant.system.integration.ubereats.dto;

import java.util.List;

/** Minimum production data only; no eater, address, phone, payment or courier data. */
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
        List<String> issues) {
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
