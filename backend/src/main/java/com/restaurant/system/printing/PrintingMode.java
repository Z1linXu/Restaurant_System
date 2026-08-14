package com.restaurant.system.printing;

import java.util.Locale;

public final class PrintingMode {

    public static final String REAL = "REAL";
    public static final String MOCK = "MOCK";
    public static final String DISABLED = "DISABLED";
    public static final String PAD_DIRECT = "PAD_DIRECT";

    private PrintingMode() {
    }

    public static String normalize(String value) {
        return normalizeOrDefault(value, DISABLED);
    }

    public static String normalizeOrDefault(String value, String fallback) {
        String normalized = normalizeOrNull(value);
        return normalized == null ? fallback : normalized;
    }

    public static String normalizeRequired(String value) {
        String normalized = normalizeOrNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Unsupported printing mode: " + display(value));
        }
        return normalized;
    }

    public static String normalizeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (REAL.equals(normalized) || MOCK.equals(normalized) || DISABLED.equals(normalized) || PAD_DIRECT.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "<blank>" : value.trim().toUpperCase(Locale.ROOT);
    }
}
