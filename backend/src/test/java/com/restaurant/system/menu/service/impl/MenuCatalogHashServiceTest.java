package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.restaurant.system.menu.dto.MenuCatalogResponse;
import com.restaurant.system.menu.dto.StoreComboConfigurationResponse;
import com.restaurant.system.menu.dto.StorePricingPolicyResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuCatalogHashServiceTest {

    private final MenuCatalogHashService service = new MenuCatalogHashService();

    @Test
    void hashIsDeterministicAndExcludesGeneratedTimestamp() {
        MenuCatalogResponse catalog = catalog();
        String first = service.calculate(catalog);

        catalog.generated_at = catalog.generated_at.plusHours(2);

        assertEquals(first, service.calculate(catalog));
        assertEquals("fnv1a32:07ab0e4f", first);
    }

    @Test
    void hashChangesWhenMenuContentChanges() {
        MenuCatalogResponse catalog = catalog();
        String before = service.calculate(catalog);

        catalog.categories.get(0).items.get(0).name_zh = "改名牛肉面";

        assertNotEquals(before, service.calculate(catalog));
    }

    @Test
    void hashChangesWhenStorePricingPolicyChanges() {
        MenuCatalogResponse catalog = catalog();
        String before = service.calculate(catalog);

        catalog.pricing_policy.size_large_delta = new BigDecimal("3.00");

        assertNotEquals(before, service.calculate(catalog));
    }

    @Test
    void hashChangesWhenStoreComboConfigurationChanges() {
        MenuCatalogResponse catalog = catalog();
        String before = service.calculate(catalog);

        catalog.combo_configuration.groups.get(0).components.get(1).enabled = false;
        catalog.combo_configuration.groups.get(0).default_component_code = "combo_tea_egg";

        assertNotEquals(before, service.calculate(catalog));
    }

    private MenuCatalogResponse catalog() {
        MenuCatalogResponse.OptionResponse option = new MenuCatalogResponse.OptionResponse(
            31L,
            "spicy_level",
            "medium_spicy",
            "SPICY_LEVEL",
            null,
            2,
            "中辣",
            "Medium",
            BigDecimal.ZERO,
            true
        );
        MenuCatalogResponse.ItemResponse item = new MenuCatalogResponse.ItemResponse(
            21L,
            11L,
            3L,
            "传统牛肉面",
            "Traditional Beef Noodle",
            "traditional_beef_noodle",
            "noodle",
            new BigDecimal("16.00"),
            true,
            false,
            10,
            List.of(option)
        );
        MenuCatalogResponse.CategoryResponse category = new MenuCatalogResponse.CategoryResponse(
            11L,
            "SOUP_NOODLE",
            "汤面",
            "Soup Noodle",
            1,
            true,
            List.of(item)
        );
        return new MenuCatalogResponse(
            1L,
            9L,
            7L,
            LocalDateTime.of(2026, 7, 13, 10, 0),
            "menu-catalog-v3",
            "stable-option-semantics-v1",
            new MenuCatalogResponse.TaxPolicyResponse(
                new BigDecimal("0.14975"),
                "14.975%",
                "ca-qc-tax-2026-01"
            ),
            pricingPolicy(),
            comboConfiguration(),
            List.of(category)
        );
    }

    private StoreComboConfigurationResponse comboConfiguration() {
        StoreComboConfigurationResponse configuration = new StoreComboConfigurationResponse();
        configuration.store_id = 1L;
        configuration.menu_revision = 7L;
        StoreComboConfigurationResponse.GroupResponse eggs = new StoreComboConfigurationResponse.GroupResponse();
        eggs.group_id = 101L;
        eggs.group_code = "COMBO_EGG";
        eggs.component_group = "COMBO_EGG";
        eggs.name_zh = "蛋类";
        eggs.name_en = "Egg";
        eggs.selection_rule = "EXACTLY_ONE";
        eggs.required = true;
        eggs.enabled = true;
        eggs.display_order = 10;
        eggs.default_component_code = "combo_tea_egg";
        eggs.components = List.of(
            component(1001L, 101L, "COMBO_EGG", "combo_tea_egg", "卤蛋", "Tea Egg", true, 10, true,
                null, null, null, null, "NO_KITCHEN_TASK"),
            component(1002L, 101L, "COMBO_EGG", "combo_fried_egg", "煎蛋", "Fried Egg", true, 20, false,
                null, null, null, null, "NO_KITCHEN_TASK")
        );
        StoreComboConfigurationResponse.GroupResponse sides = new StoreComboConfigurationResponse.GroupResponse();
        sides.group_id = 102L;
        sides.group_code = "COMBO_SIDE";
        sides.component_group = "COMBO_SIDE";
        sides.name_zh = "小菜";
        sides.name_en = "Side";
        sides.selection_rule = "EXACTLY_ONE";
        sides.required = true;
        sides.enabled = true;
        sides.display_order = 20;
        sides.default_component_code = "combo_edamame";
        sides.components = List.of(
            component(2001L, 102L, "COMBO_SIDE", "combo_edamame", "毛豆", "Edamame", true, 10, true,
                501L, "edamame", "毛豆", "Edamame", "LINKED_MENU_ITEM"),
            component(2002L, 102L, "COMBO_SIDE", "combo_shredded_potato", "土豆丝", "Shredded Potato", true, 20, false,
                502L, "shredded_potato", "土豆丝", "Shredded Potato", "LEGACY_COMBO_SIDE_TASK"),
            component(2003L, 102L, "COMBO_SIDE", "combo_cucumber_salad", "拌黄瓜", "Cucumber Salad", true, 30, false,
                503L, "cucumber_salad", "拌黄瓜", "Cucumber Salad", "LEGACY_COMBO_SIDE_TASK")
        );
        configuration.groups = List.of(eggs, sides);
        return configuration;
    }

    private StoreComboConfigurationResponse.ComponentResponse component(
        Long id,
        Long groupId,
        String group,
        String code,
        String nameZh,
        String nameEn,
        boolean enabled,
        int displayOrder,
        boolean defaultComponent,
        Long linkedMenuItemId,
        String linkedMenuItemSku,
        String linkedMenuItemNameZh,
        String linkedMenuItemNameEn,
        String businessBehavior
    ) {
        StoreComboConfigurationResponse.ComponentResponse component = new StoreComboConfigurationResponse.ComponentResponse();
        component.id = id;
        component.group_id = groupId;
        component.component_group = group;
        component.component_code = code;
        component.name_zh = nameZh;
        component.name_en = nameEn;
        component.enabled = enabled;
        component.display_order = displayOrder;
        component.is_default = defaultComponent;
        component.linked_menu_item_id = linkedMenuItemId;
        component.linked_menu_item_sku = linkedMenuItemSku;
        component.linked_menu_item_name_zh = linkedMenuItemNameZh;
        component.linked_menu_item_name_en = linkedMenuItemNameEn;
        component.business_behavior = businessBehavior;
        return component;
    }

    private StorePricingPolicyResponse pricingPolicy() {
        StorePricingPolicyResponse policy = new StorePricingPolicyResponse();
        policy.store_id = 1L;
        policy.policy_revision = 1L;
        policy.size_small_delta = new BigDecimal("-2.00");
        policy.size_regular_delta = new BigDecimal("0.00");
        policy.size_large_delta = new BigDecimal("2.00");
        policy.combo_delta = new BigDecimal("5.00");
        return policy;
    }
}
