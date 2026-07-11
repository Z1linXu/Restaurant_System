package com.restaurant.system.printing.semantic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.order.entity.OrderItemOption;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OptionSemanticResolverTest {

    @Mock
    private MenuItemOptionRepository menuItemOptionRepository;

    private OptionSemanticResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OptionSemanticResolver(menuItemOptionRepository);
    }

    @Test
    void friedEggUsesStableSnapshotCodeEvenWhenDisplayNameChanges() {
        OrderItemOption option = option("fried_egg", "ADD_ON", "Sunny Egg", "太阳蛋");

        assertTrue(resolver.isFriedEgg(option));
        assertFalse(resolver.isComboFriedEgg(option));
    }

    @Test
    void comboFriedEggUsesStableGroupAndCodeWithoutCountingAsExtraEgg() {
        OrderItemOption option = option("combo_fried_egg", "COMBO_EGG", "Combo Sunny Egg", "套餐太阳蛋");

        assertFalse(resolver.isFriedEgg(option));
        assertTrue(resolver.isComboFriedEgg(option));
    }

    @Test
    void comboTeaEggDoesNotRouteAsComboFriedEgg() {
        OrderItemOption option = option("combo_tea_egg", "COMBO_EGG", "Combo Tea Egg", "套餐卤蛋");

        assertFalse(resolver.isFriedEgg(option));
        assertFalse(resolver.isComboFriedEgg(option));
    }

    @Test
    void currentOptionCodeIsUsedOnlyAsFallbackForOldSnapshots() {
        OrderItemOption option = option(null, null, "Renamed Egg", "改名鸡蛋");
        option.option_id = 88L;
        MenuItemOption current = new MenuItemOption();
        current.id = option.option_id;
        current.option_code = "fried_egg";
        current.option_group = "ADD_ON";
        when(menuItemOptionRepository.findById(option.option_id)).thenReturn(Optional.of(current));

        assertTrue(resolver.isFriedEgg(option));
    }

    private OrderItemOption option(String code, String group, String nameEn, String nameZh) {
        OrderItemOption option = new OrderItemOption();
        option.option_code_snapshot = code;
        option.option_group_snapshot = group;
        option.option_name_snapshot_en = nameEn;
        option.option_name_snapshot_zh = nameZh;
        return option;
    }
}
