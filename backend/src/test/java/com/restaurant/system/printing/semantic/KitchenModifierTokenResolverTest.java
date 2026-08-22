package com.restaurant.system.printing.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import org.junit.jupiter.api.Test;

class KitchenModifierTokenResolverTest {

    @Test
    void predefinedAddonKeepsExistingTokenAndQuantity() {
        OrderItemOption option = addon("tea_egg", "加蛋", 2);

        assertThat(KitchenModifierTokenResolver.resolveAddon(option, PrintingDisplayRuleContext.defaultContext()))
            .isEqualTo("+蛋x2");
    }

    @Test
    void storeLocalAndLegacyAddonsFailVisibleFromFrozenLabels() {
        OrderItemOption storeLocal = addon("s", "加牛筋", 1);
        OrderItemOption legacy = addon(null, "加牛筋", 1);

        assertThat(KitchenModifierTokenResolver.resolveAddon(storeLocal, PrintingDisplayRuleContext.defaultContext()))
            .isEqualTo("+牛筋");
        assertThat(KitchenModifierTokenResolver.resolveAddon(legacy, PrintingDisplayRuleContext.defaultContext()))
            .isEqualTo("+牛筋");
    }

    @Test
    void displayRuleOverrideWinsWithoutBecomingAdmissionControl() {
        PrintingDisplayRuleContext rules = context("""
            {
              "dictionaries": {
                "MODIFIER_ADD": [["s", "+筋H"]],
                "MODIFIER_REMOVE": []
              }
            }
            """);

        assertThat(KitchenModifierTokenResolver.resolveAddon(addon("s", "加牛筋", 1), rules))
            .isEqualTo("+筋H");
        assertThat(KitchenModifierTokenResolver.resolveAddon(addon("unknown_code", "加豆腐", 1), rules))
            .isEqualTo("+豆腐");
    }

    @Test
    void storeScopedRuleDoesNotLeakAndSourceTypeDoesNotChangeSnapshotPolicy() {
        PrintingDisplayRuleContext storeARules = context("""
            {
              "dictionaries": {
                "MODIFIER_ADD": [["s", "+A店牛筋"]],
                "MODIFIER_REMOVE": []
              }
            }
            """);
        PrintingDisplayRuleContext storeBRules = context("""
            {
              "dictionaries": {
                "MODIFIER_ADD": [],
                "MODIFIER_REMOVE": []
              }
            }
            """);

        OrderItemOption storeOnly = addon("s", "加牛筋", 1);
        OrderItemOption masterDerived = addon("tea_egg", "加蛋", 1);

        assertThat(KitchenModifierTokenResolver.resolveAddon(storeOnly, storeARules)).isEqualTo("+A店牛筋");
        assertThat(KitchenModifierTokenResolver.resolveAddon(storeOnly, storeBRules)).isEqualTo("+牛筋");
        assertThat(KitchenModifierTokenResolver.resolveAddon(masterDerived, storeBRules)).isEqualTo("+蛋");
    }

    @Test
    void removeUsesSameFailVisiblePolicyAndKeepsKnownFallback() {
        OrderItemOption known = remove("remove_bok_choy", "走上海青");
        OrderItemOption dynamic = remove("remove_custom", "不要牛筋");
        OrderItemOption legacy = remove(null, "不要葱");

        assertThat(KitchenModifierTokenResolver.resolveRemove(known, PrintingDisplayRuleContext.defaultContext()))
            .isEqualTo("走上海青");
        assertThat(KitchenModifierTokenResolver.resolveRemove(dynamic, PrintingDisplayRuleContext.defaultContext()))
            .isEqualTo("不要牛筋");
        assertThat(KitchenModifierTokenResolver.resolveRemove(legacy, PrintingDisplayRuleContext.defaultContext()))
            .isEqualTo("走葱");
    }

    @Test
    void sameCodeCanRemainTrueAddonWhileComboGroupIsNotRenderedAsModifier() {
        OrderItemOption trueAddon = addon("combo_edamame", "毛豆", 1);
        OrderItemOption comboPriceSelection = addon("combo", "套餐", 1);
        comboPriceSelection.option_group_snapshot = "COMBO";

        assertThat(KitchenModifierTokenResolver.resolveAddon(trueAddon, PrintingDisplayRuleContext.defaultContext()))
            .isEqualTo("+毛豆");
        assertThat(KitchenModifierTokenResolver.resolveAddon(comboPriceSelection, PrintingDisplayRuleContext.defaultContext()))
            .isNull();
    }

    private OrderItemOption addon(String code, String label, int quantity) {
        OrderItemOption option = new OrderItemOption();
        option.option_type_snapshot = "addon";
        option.option_group_snapshot = "ADD_ON";
        option.option_code_snapshot = code;
        option.option_name_snapshot_zh = label;
        option.quantity = quantity;
        return option;
    }

    private OrderItemOption remove(String code, String label) {
        OrderItemOption option = new OrderItemOption();
        option.option_type_snapshot = "remove";
        option.option_group_snapshot = "REMOVE";
        option.option_code_snapshot = code;
        option.option_name_snapshot_zh = label;
        option.quantity = 1;
        return option;
    }

    private PrintingDisplayRuleContext context(String json) {
        return new PrintingDisplayRuleContext(null, 1, "test", StoreProfileCanonicalJson.parse(json));
    }
}
