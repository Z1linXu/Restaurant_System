package com.restaurant.system.printing.semantic;

import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves kitchen-facing modifier tokens from frozen order-option semantics.
 * Display rules may override presentation, but never decide whether a valid
 * modifier is printable.
 */
public final class KitchenModifierTokenResolver {

    private static final Set<String> REMOVE_PREFIXES = Set.of("走", "少", "不要", "无");

    private KitchenModifierTokenResolver() {
    }

    public static String resolveAddon(OrderItemOption option, PrintingDisplayRuleContext printingRules) {
        if (option == null) {
            return null;
        }
        return resolveAddon(
            option.option_group_snapshot,
            option.option_code_snapshot,
            option.option_name_snapshot_zh,
            option.option_name_snapshot_en,
            option.quantity,
            printingRules
        );
    }

    public static String resolveAddon(
        String optionGroup,
        String semanticCode,
        String labelZh,
        String labelEn,
        Integer quantity,
        PrintingDisplayRuleContext printingRules
    ) {
        String code = stable(semanticCode);
        if ("COMBO".equalsIgnoreCase(trim(optionGroup)) || "combo".equals(code)) {
            return null;
        }

        String fallback = knownAddonToken(code);
        if (fallback == null) {
            fallback = knownLegacyAddonPresentation(labelZh);
        }
        if (fallback == null) {
            fallback = addonFallback(labelZh, labelEn, semanticCode);
        }
        String token = printingRules == null
            ? fallback
            : printingRules.resolveModifierToken("MODIFIER_ADD", semanticCode, fallback);
        if (token == null || token.isBlank()) {
            token = fallback;
        }
        int resolvedQuantity = quantity == null || quantity < 1 ? 1 : quantity;
        return resolvedQuantity > 1 ? token + "x" + resolvedQuantity : token;
    }

    public static String resolveRemove(OrderItemOption option, PrintingDisplayRuleContext printingRules) {
        if (option == null) {
            return null;
        }
        return resolveRemove(
            option.option_code_snapshot,
            option.option_name_snapshot_zh,
            option.option_name_snapshot_en,
            printingRules
        );
    }

    public static String resolveRemove(
        String semanticCode,
        String labelZh,
        String labelEn,
        PrintingDisplayRuleContext printingRules
    ) {
        String code = stable(stripRemovePrefix(semanticCode));
        String fallback = knownRemoveToken(code);
        if (fallback == null) {
            fallback = knownLegacyRemovePresentation(labelZh);
        }
        if (fallback == null) {
            fallback = removeFallback(labelZh, labelEn, semanticCode);
        }
        String token = printingRules == null
            ? fallback
            : printingRules.resolveModifierToken("MODIFIER_REMOVE", code, fallback);
        return token == null || token.isBlank() ? fallback : token;
    }

    private static String knownAddonToken(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "extra_noodle" -> "+面";
            case "tea_egg", "combo_tea_egg" -> "+蛋";
            case "fried_egg", "combo_fried_egg" -> "+煎";
            case "extra_meat" -> "+肉";
            case "extra_radish" -> "+萝";
            case "bok_choy" -> "加上海青";
            case "cilantro" -> "+香";
            case "green_onion" -> "+葱";
            case "extra_sauce" -> "+酱";
            case "broccoli" -> "+西兰";
            case "cabbage" -> "+包";
            case "corn" -> "+玉";
            case "seaweed" -> "+海";
            case "mushroom" -> "+菇";
            case "carrot_slice" -> "+胡";
            case "combo_edamame" -> "+毛豆";
            case "combo_shredded_potato" -> "+土豆";
            case "combo_cucumber_salad" -> "+黄瓜";
            default -> null;
        };
    }

    private static String knownRemoveToken(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "cilantro" -> "走香";
            case "green_onion" -> "走葱";
            case "beef" -> "走牛";
            case "radish" -> "走萝";
            case "noodle" -> "走面";
            case "less_noodle" -> "少面";
            case "bok_choy" -> "走上海青";
            case "broccoli" -> "走西兰";
            case "corn" -> "走玉米";
            case "mushroom" -> "走菇";
            case "seaweed" -> "走海";
            case "carrot" -> "走胡";
            case "cucumber" -> "走黄瓜";
            case "edamame" -> "走毛豆";
            case "peanut" -> "走花生";
            case "cabbage" -> "走包";
            case "meat" -> "走肉";
            case "green_pepper" -> "走青椒";
            default -> null;
        };
    }

    private static String knownLegacyAddonPresentation(String labelZh) {
        String label = trim(labelZh);
        if (label == null) {
            return null;
        }
        return switch (label) {
            case "加面" -> "+面";
            case "加蛋", "套餐卤蛋" -> "+蛋";
            case "加煎蛋", "套餐煎蛋" -> "+煎";
            case "加肉" -> "+肉";
            case "加萝卜" -> "+萝";
            case "加上海青" -> "加上海青";
            case "加香菜" -> "+香";
            case "加葱" -> "+葱";
            case "加酱" -> "+酱";
            case "加西兰花" -> "+西兰";
            case "加包菜" -> "+包";
            case "加玉米" -> "+玉";
            case "加海菜" -> "+海";
            case "加蘑菇" -> "+菇";
            case "加胡萝卜片" -> "+胡";
            case "套餐毛豆" -> "+毛豆";
            case "套餐土豆丝" -> "+土豆";
            case "套餐拌黄瓜" -> "+黄瓜";
            default -> null;
        };
    }

    private static String knownLegacyRemovePresentation(String labelZh) {
        String label = trim(labelZh);
        if (label == null) {
            return null;
        }
        return switch (label) {
            case "走香菜", "不要香菜" -> "走香";
            case "走葱", "不要葱", "走洋葱" -> "走葱";
            case "走牛肉" -> "走牛";
            case "走萝卜" -> "走萝";
            case "走面", "No Noodle" -> "走面";
            case "少面" -> "少面";
            case "走上海青" -> "走上海青";
            case "走西兰花" -> "走西兰";
            case "走玉米" -> "走玉米";
            case "走蘑菇" -> "走菇";
            case "走海菜" -> "走海";
            case "走胡萝卜片", "走胡萝卜" -> "走胡";
            case "走黄瓜" -> "走黄瓜";
            case "走毛豆" -> "走毛豆";
            case "走花生", "走花生碎" -> "走花生";
            case "走包菜" -> "走包";
            case "走肉" -> "走肉";
            case "走青椒" -> "走青椒";
            default -> null;
        };
    }

    private static String addonFallback(String labelZh, String labelEn, String semanticCode) {
        String label = firstLabel(labelZh, labelEn, semanticCode, "未知加项");
        if (label.startsWith("+")) {
            return label;
        }
        if (label.startsWith("加") && label.length() > 1) {
            return "+" + label.substring(1).trim();
        }
        return "+" + label;
    }

    private static String removeFallback(String labelZh, String labelEn, String semanticCode) {
        String label = firstLabel(labelZh, labelEn, semanticCode, "未知减项");
        for (String prefix : REMOVE_PREFIXES) {
            if (label.startsWith(prefix)) {
                return label;
            }
        }
        return "走" + label;
    }

    private static String firstLabel(String labelZh, String labelEn, String semanticCode, String unknown) {
        String label = trim(labelZh);
        if (label == null) {
            label = trim(labelEn);
        }
        if (label == null) {
            label = trim(semanticCode);
        }
        return label == null ? unknown : label;
    }

    private static String stripRemovePrefix(String code) {
        String trimmed = trim(code);
        if (trimmed == null) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT).startsWith("remove_")
            ? trimmed.substring("remove_".length())
            : trimmed;
    }

    private static String stable(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
