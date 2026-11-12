package com.restaurant.system.menu.display;

import java.util.Map;
import java.util.regex.Pattern;

/** Stable merchant SKU identity; does not rename menu records or historical snapshots. */
public final class MenuCodeFormatter {
    private static final Pattern ALLO_CODE = Pattern.compile("^AN_([A-E])([1-9][0-9]*)$");
    private static final Map<String, Integer> LAST_NUMBER = Map.of("A", 9, "B", 8, "C", 6, "D", 6, "E", 3);
    private MenuCodeFormatter() {}

    public static String code(String sku) {
        if (sku == null) return null;
        var match = ALLO_CODE.matcher(sku.trim());
        if (!match.matches()) return null;
        try {
            return Integer.parseInt(match.group(2)) <= LAST_NUMBER.get(match.group(1))
                ? match.group(1) + match.group(2) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String display(String sku, String name) {
        String code = code(sku);
        if (code == null || name == null) return name;
        String plain = name.trim().replaceFirst("(?i)^(?:" + code + "(?:\\s*[-.:]\\s*|\\s+))+", "");
        return plain.isBlank() ? code : code + " " + plain;
    }
}
