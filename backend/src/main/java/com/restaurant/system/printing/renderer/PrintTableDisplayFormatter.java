package com.restaurant.system.printing.renderer;

final class PrintTableDisplayFormatter {

    private PrintTableDisplayFormatter() {
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
