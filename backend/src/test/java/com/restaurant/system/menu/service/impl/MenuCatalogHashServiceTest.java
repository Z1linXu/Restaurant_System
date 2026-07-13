package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.restaurant.system.menu.dto.MenuCatalogResponse;
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
        assertEquals("fnv1a32:6b4cec40", first);
    }

    @Test
    void hashChangesWhenMenuContentChanges() {
        MenuCatalogResponse catalog = catalog();
        String before = service.calculate(catalog);

        catalog.categories.get(0).items.get(0).name_zh = "改名牛肉面";

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
            "menu-catalog-v2",
            "stable-option-semantics-v1",
            new MenuCatalogResponse.TaxPolicyResponse(
                new BigDecimal("0.14975"),
                "14.975%",
                "ca-qc-tax-2026-01"
            ),
            List.of(category)
        );
    }
}
