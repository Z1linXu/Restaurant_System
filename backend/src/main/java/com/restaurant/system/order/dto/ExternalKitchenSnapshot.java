package com.restaurant.system.order.dto;

import java.util.List;

/** Frozen display-only external content. It never grants menu identity or station routing. */
public record ExternalKitchenSnapshot(
        String itemId, boolean rawRoot, String itemName, int quantity, String notes,
        List<Modifier> modifiers) {
    public record Modifier(String id, String name, int quantity, boolean removed, String notes,
                           List<Modifier> modifiers, List<Context> parentContext) {}
    public record Context(String id, String name) {}
}
