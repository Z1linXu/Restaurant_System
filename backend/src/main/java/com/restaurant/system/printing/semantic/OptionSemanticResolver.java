package com.restaurant.system.printing.semantic;

import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.order.entity.OrderItemOption;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class OptionSemanticResolver {

    private static final String GROUP_COMBO_EGG = "COMBO_EGG";

    private final MenuItemOptionRepository menuItemOptionRepository;

    public OptionSemanticResolver(MenuItemOptionRepository menuItemOptionRepository) {
        this.menuItemOptionRepository = menuItemOptionRepository;
    }

    public boolean isFriedEgg(OrderItemOption option) {
        String snapshotCode = normalizeCode(option == null ? null : option.option_code_snapshot);
        if (isExtraFriedEggCode(snapshotCode)) {
            return true;
        }

        String currentCode = resolveCurrentOptionCode(option);
        if (isExtraFriedEggCode(currentCode)) {
            return true;
        }

        return isLegacyExtraFriedEggLabel(option);
    }

    public boolean isComboFriedEgg(OrderItemOption option) {
        String snapshotCode = normalizeCode(option == null ? null : option.option_code_snapshot);
        String snapshotGroup = normalizeCode(option == null ? null : option.option_group_snapshot);
        if (GROUP_COMBO_EGG.equals(snapshotGroup) && isComboFriedEggCode(snapshotCode)) {
            return true;
        }
        if (isComboFriedEggCode(snapshotCode)) {
            return true;
        }

        MenuItemOption current = resolveCurrentOption(option);
        String currentCode = normalizeCode(current == null ? null : current.option_code);
        String currentGroup = normalizeCode(current == null ? null : current.option_group);
        if (GROUP_COMBO_EGG.equals(currentGroup) && isComboFriedEggCode(currentCode)) {
            return true;
        }
        if (isComboFriedEggCode(currentCode)) {
            return true;
        }

        return isLegacyComboFriedEggLabel(option);
    }

    private String resolveCurrentOptionCode(OrderItemOption option) {
        MenuItemOption current = resolveCurrentOption(option);
        return normalizeCode(current == null ? null : current.option_code);
    }

    private MenuItemOption resolveCurrentOption(OrderItemOption option) {
        if (option == null || option.option_id == null) {
            return null;
        }
        return menuItemOptionRepository.findById(option.option_id).orElse(null);
    }

    private boolean isExtraFriedEggCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        if (isComboFriedEggCode(code)) {
            return false;
        }
        return "FRIED_EGG".equals(code)
            || code.endsWith("_FRIED_EGG")
            || code.endsWith("_EXTRA_FRIED_EGG")
            || code.endsWith("_ADDON_EXTRA_FRIED_EGG");
    }

    private boolean isComboFriedEggCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return "COMBO_FRIED_EGG".equals(code) || code.endsWith("_COMBO_FRIED_EGG");
    }

    private boolean isLegacyExtraFriedEggLabel(OrderItemOption option) {
        if (option == null) {
            return false;
        }
        String zh = trim(option.option_name_snapshot_zh);
        if ("加煎蛋".equals(zh)) {
            return true;
        }
        String en = normalizeText(option.option_name_snapshot_en);
        return en.contains("fried egg") && !en.contains("combo");
    }

    private boolean isLegacyComboFriedEggLabel(OrderItemOption option) {
        if (option == null) {
            return false;
        }
        String zh = trim(option.option_name_snapshot_zh);
        if ("套餐煎蛋".equals(zh)) {
            return true;
        }
        String en = normalizeText(option.option_name_snapshot_en);
        return en.contains("combo") && en.contains("fried egg");
    }

    private String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
