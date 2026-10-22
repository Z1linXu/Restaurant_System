package com.restaurant.system.menu.remove;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Exact identity aliases only. Different ingredients and operations are never inferred from names. */
public final class RemoveSemantics {
    private static final Map<String, String> BASELINE;
    static {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("cilantro", "走香");
        values.put("green_onion", "走葱");
        values.put("beef", "走牛");
        values.put("radish", "走萝");
        values.put("noodle", "走面");
        values.put("less_noodle", "少面");
        values.put("bok_choy", "走上海青");
        values.put("broccoli", "走西兰");
        values.put("corn", "走玉米");
        values.put("mushroom", "走菇");
        values.put("seaweed", "走海");
        values.put("carrot", "走胡");
        values.put("cucumber", "走黄瓜");
        values.put("edamame", "走毛豆");
        values.put("peanut", "走花生");
        values.put("cabbage", "走包");
        values.put("meat", "走肉");
        values.put("green_pepper", "走青椒");
        BASELINE = Collections.unmodifiableMap(values);
    }

    private RemoveSemantics() {}

    public static String canonicalSemantic(String code) {
        if (code == null || code.isBlank()) return null;
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        String semantic = normalized.startsWith("remove_") ? normalized.substring("remove_".length()) : normalized;
        return semantic.isBlank() ? null : semantic;
    }

    public static Map<String, String> baselineDefaults() { return BASELINE; }

    public static String defaultPrintText(String code) { return BASELINE.get(canonicalSemantic(code)); }
}
