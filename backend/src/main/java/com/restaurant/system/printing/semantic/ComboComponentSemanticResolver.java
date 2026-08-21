package com.restaurant.system.printing.semantic;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.OrderItemOption;
import java.util.Locale;

/** Stable semantic boundary for the legacy Combo sides that own synthetic kitchen tasks. */
public final class ComboComponentSemanticResolver {

    public static final int SYNTHETIC_SIDE_TASK_PRIORITY = 100;

    private static final String GROUP_COMBO_SIDE = "COMBO_SIDE";
    private static final String GROUP_COMBO_SIDE_REMOVE = "COMBO_SIDE_REMOVE";

    private ComboComponentSemanticResolver() {
    }

    public static StandaloneSide resolveStandaloneSide(OrderItemOption option) {
        if (option == null || !GROUP_COMBO_SIDE.equals(normalize(option.option_group_snapshot))) {
            return null;
        }
        return switch (normalize(option.option_code_snapshot)) {
            case "COMBO_EDAMAME" -> new StandaloneSide("combo_edamame", "毛豆", "Edamame");
            case "COMBO_SHREDDED_POTATO" -> new StandaloneSide("combo_shredded_potato", "土豆", "Shredded Potato");
            case "COMBO_CUCUMBER_SALAD" -> new StandaloneSide("combo_cucumber_salad", "黄瓜", "Cucumber Salad");
            default -> null;
        };
    }

    public static boolean isStandaloneSide(OrderItemOption option) {
        return resolveStandaloneSide(option) != null;
    }

    public static boolean isSideRemoval(OrderItemOption option) {
        return option != null && GROUP_COMBO_SIDE_REMOVE.equals(normalize(option.option_group_snapshot));
    }

    public static boolean isSyntheticSideTask(KitchenTask task) {
        return task != null && Integer.valueOf(SYNTHETIC_SIDE_TASK_PRIORITY).equals(task.priority);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record StandaloneSide(String code, String labelZh, String labelEn) {
    }
}
