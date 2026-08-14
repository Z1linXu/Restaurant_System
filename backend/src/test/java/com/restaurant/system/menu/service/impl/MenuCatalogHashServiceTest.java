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
        assertEquals("fnv1a32:7c11b6d9", first);
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
        eggs.component_group = "COMBO_EGG";
        eggs.name_zh = "蛋类";
        eggs.name_en = "Egg";
        eggs.default_component_code = "combo_tea_egg";
        eggs.components = List.of(
            component("COMBO_EGG", "combo_tea_egg", "卤蛋", "Tea Egg", true, 10, true),
            component("COMBO_EGG", "combo_fried_egg", "煎蛋", "Fried Egg", true, 20, false)
        );
        StoreComboConfigurationResponse.GroupResponse sides = new StoreComboConfigurationResponse.GroupResponse();
        sides.component_group = "COMBO_SIDE";
        sides.name_zh = "小菜";
        sides.name_en = "Side";
        sides.default_component_code = "combo_edamame";
        sides.components = List.of(
            component("COMBO_SIDE", "combo_edamame", "毛豆", "Edamame", true, 10, true),
            component("COMBO_SIDE", "combo_shredded_potato", "土豆丝", "Shredded Potato", true, 20, false),
            component("COMBO_SIDE", "combo_cucumber_salad", "拌黄瓜", "Cucumber Salad", true, 30, false)
        );
        configuration.groups = List.of(eggs, sides);
        return configuration;
    }

    private StoreComboConfigurationResponse.ComponentResponse component(
        String group,
        String code,
        String nameZh,
        String nameEn,
        boolean enabled,
        int displayOrder,
        boolean defaultComponent
    ) {
        StoreComboConfigurationResponse.ComponentResponse component = new StoreComboConfigurationResponse.ComponentResponse();
        component.component_group = group;
        component.component_code = code;
        component.name_zh = nameZh;
        component.name_en = nameEn;
        component.enabled = enabled;
        component.display_order = displayOrder;
        component.is_default = defaultComponent;
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
