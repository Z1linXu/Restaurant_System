package com.restaurant.system.printing.semantic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.OrderItemOption;
import org.junit.jupiter.api.Test;

class ComboComponentSemanticResolverTest {

    @Test
    void resolvesStandaloneSideFromStableGroupAndCodeWithoutUsingDisplayLabel() {
        OrderItemOption option = option("COMBO_SIDE", "combo_edamame", "本店今日小菜");

        ComboComponentSemanticResolver.StandaloneSide side = ComboComponentSemanticResolver.resolveStandaloneSide(option);

        assertNotNull(side);
        assertTrue(ComboComponentSemanticResolver.isStandaloneSide(option));
    }

    @Test
    void keepsSameNameAndCodeAddonWhenSemanticGroupIsNotComboSide() {
        OrderItemOption option = option("ADD_ON", "combo_edamame", "毛豆");

        assertFalse(ComboComponentSemanticResolver.isStandaloneSide(option));
    }

    @Test
    void failsSafeWhenHistoricalStableIdentityIsIncomplete() {
        assertFalse(ComboComponentSemanticResolver.isStandaloneSide(option(null, "combo_edamame", "套餐毛豆")));
        assertFalse(ComboComponentSemanticResolver.isStandaloneSide(option("COMBO_SIDE", null, "套餐毛豆")));
    }

    @Test
    void identifiesSyntheticTaskWithoutInferringStation() {
        KitchenTask task = new KitchenTask();
        task.station_code = "WOK";
        task.priority = ComboComponentSemanticResolver.SYNTHETIC_SIDE_TASK_PRIORITY;

        assertTrue(ComboComponentSemanticResolver.isSyntheticSideTask(task));
    }

    private OrderItemOption option(String group, String code, String label) {
        OrderItemOption option = new OrderItemOption();
        option.option_type_snapshot = "addon";
        option.option_group_snapshot = group;
        option.option_code_snapshot = code;
        option.option_name_snapshot_zh = label;
        return option;
    }
}
