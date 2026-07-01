package com.restaurant.system.printing;

public final class PrintingMode {

    public static final String REAL = "REAL";
    public static final String MOCK = "MOCK";
    public static final String DISABLED = "DISABLED";
    public static final String PAD_DIRECT = "PAD_DIRECT";

    private PrintingMode() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return REAL;
        }
        String normalized = value.trim().toUpperCase();
        if (MOCK.equals(normalized) || DISABLED.equals(normalized) || PAD_DIRECT.equals(normalized)) {
            return normalized;
        }
        return REAL;
    }
}
