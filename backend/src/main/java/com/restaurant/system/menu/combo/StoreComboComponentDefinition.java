package com.restaurant.system.menu.combo;

import com.restaurant.system.menu.entity.MenuItemOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum StoreComboComponentDefinition {
    TEA_EGG("COMBO_EGG", "combo_tea_egg", "卤蛋", "Tea Egg", 10),
    FRIED_EGG("COMBO_EGG", "combo_fried_egg", "煎蛋", "Fried Egg", 20),
    EDAMAME("COMBO_SIDE", "combo_edamame", "毛豆", "Edamame", 10),
    SHREDDED_POTATO("COMBO_SIDE", "combo_shredded_potato", "土豆丝", "Shredded Potato", 20),
    CUCUMBER_SALAD("COMBO_SIDE", "combo_cucumber_salad", "拌黄瓜", "Cucumber Salad", 30);

    public final String componentGroup;
    public final String componentCode;
    public final String nameZh;
    public final String nameEn;
    public final int displayOrder;

    StoreComboComponentDefinition(
        String componentGroup,
        String componentCode,
        String nameZh,
        String nameEn,
        int displayOrder
    ) {
        this.componentGroup = componentGroup;
        this.componentCode = componentCode;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.displayOrder = displayOrder;
    }

    public static List<StoreComboComponentDefinition> valuesForGroup(String componentGroup) {
        String normalizedGroup = normalizeGroup(componentGroup);
        return Arrays.stream(values())
            .filter(definition -> definition.componentGroup.equals(normalizedGroup))
            .toList();
    }

    public static Optional<StoreComboComponentDefinition> fromGroupAndCode(String componentGroup, String componentCode) {
        String normalizedGroup = normalizeGroup(componentGroup);
        String normalizedCode = normalizeCode(componentCode);
        return Arrays.stream(values())
            .filter(definition -> definition.componentGroup.equals(normalizedGroup))
            .filter(definition -> definition.componentCode.equals(normalizedCode))
            .findFirst();
    }

    public static Optional<StoreComboComponentDefinition> fromOption(MenuItemOption option) {
        if (option == null) {
            return Optional.empty();
        }
        String group = normalizeGroup(option.option_group);
        if (!"COMBO_EGG".equals(group) && !"COMBO_SIDE".equals(group)) {
            return Optional.empty();
        }
        return fromGroupAndCode(group, option.option_code);
    }

    public static boolean isStoreConfiguredGroup(String componentGroup) {
        String normalizedGroup = normalizeGroup(componentGroup);
        return "COMBO_EGG".equals(normalizedGroup) || "COMBO_SIDE".equals(normalizedGroup);
    }

    public static String normalizeGroup(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
