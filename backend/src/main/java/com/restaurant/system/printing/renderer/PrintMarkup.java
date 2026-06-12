package com.restaurant.system.printing.renderer;

public final class PrintMarkup {

    public static final String DOUBLE_HEIGHT_OPEN = "[[DOUBLE_HEIGHT]]";
    public static final String DOUBLE_HEIGHT_CLOSE = "[[/DOUBLE_HEIGHT]]";

    private PrintMarkup() {
    }

    public static String doubleHeight(String value) {
        return DOUBLE_HEIGHT_OPEN + value + DOUBLE_HEIGHT_CLOSE;
    }
}
