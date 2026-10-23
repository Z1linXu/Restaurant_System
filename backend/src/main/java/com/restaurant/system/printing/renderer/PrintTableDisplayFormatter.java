package com.restaurant.system.printing.renderer;

import com.restaurant.system.order.entity.Order;

final class PrintTableDisplayFormatter {

    private PrintTableDisplayFormatter() {
    }

    static boolean isDineIn(Order order) {
        return order != null && "dine_in".equalsIgnoreCase(order.order_type)
            && !order.kitchenMirror() && !ExternalOrderReceiptHeader.isPresent(order);
    }

    static String formatDineInHeader(String tableCode, boolean updateTicket) {
        String label = formatSplitTableLabel(tableCode);
        if (label == null || label.isBlank()) {
            label = "Walk-in";
        }
        return PrintMarkup.large(label) + (updateTicket ? " (UPDATE)" : "");
    }

    static String formatSplitTableLabel(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return trimmed;
        }
        if ("A".equalsIgnoreCase(trimmed)) {
            return "左";
        }
        if ("B".equalsIgnoreCase(trimmed)) {
            return "右";
        }
        return trimmed
            .replaceAll("(?i)-A\\b", "-左")
            .replaceAll("(?i)-B\\b", "-右");
    }
}
