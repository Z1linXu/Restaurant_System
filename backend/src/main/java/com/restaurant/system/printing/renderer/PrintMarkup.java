package com.restaurant.system.printing.renderer;

public final class PrintMarkup {

    public static final String DOUBLE_HEIGHT_OPEN = "[[DOUBLE_HEIGHT]]";
    public static final String DOUBLE_HEIGHT_CLOSE = "[[/DOUBLE_HEIGHT]]";
    public static final String LARGE_OPEN = "[[LARGE]]";
    public static final String LARGE_CLOSE = "[[/LARGE]]";
    public static final String SMALL_OPEN = "[[SMALL]]";
    public static final String SMALL_CLOSE = "[[/SMALL]]";

    private PrintMarkup() {
    }

    public static String doubleHeight(String value) {
        return DOUBLE_HEIGHT_OPEN + value + DOUBLE_HEIGHT_CLOSE;
    }

    public static String large(String value) {
        return LARGE_OPEN + value + LARGE_CLOSE;
    }

    public static String small(String value) {
        return SMALL_OPEN + value + SMALL_CLOSE;
    }
}
