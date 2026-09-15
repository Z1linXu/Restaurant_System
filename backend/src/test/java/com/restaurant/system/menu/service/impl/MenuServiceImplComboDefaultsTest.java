package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.restaurant.system.menu.dto.MenuRevisionResponse;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.menu.service.StorePricingPolicyService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuServiceImplComboDefaultsTest {
    @Test
    void catalogProjectsExplicitOverrideAndNullInheritanceWithoutChangingPriceOrStation() {
        MenuCategoryRepository categories = mock(MenuCategoryRepository.class);
        MenuItemRepository items = mock(MenuItemRepository.class);
        MenuRevisionService revisions = mock(MenuRevisionService.class);
        MenuServiceImpl service = new MenuServiceImpl(categories, items, mock(MenuItemOptionRepository.class),
            revisions, new MenuCatalogHashService(), mock(StorePricingPolicyService.class),
            mock(StoreComboConfigurationService.class));
        MenuCategory category = new MenuCategory();
        category.id = 8L;
        category.is_active = true;
        MenuItem item = new MenuItem();
        item.id = 22L;
        item.store_id = 9L;
        item.category_id = 8L;
        item.station_id = 3L;
        item.base_price = new BigDecimal("16.00");
        item.name_zh = "牛肉面";
        item.default_combo_egg_component_code = "combo_fried_egg";
        when(categories.findActiveByStoreId(9L)).thenReturn(List.of(category));
        when(items.findActiveByStoreId(9L)).thenReturn(List.of(item));
        when(revisions.getRevision(9L)).thenReturn(new MenuRevisionResponse(9L, 1L, 7L,
            LocalDateTime.now(), "menu-catalog-v3", "tax", "etag"));

        var explicit = service.getCatalog(9L);

        var projected = explicit.categories.get(0).items.get(0);
        assertEquals("combo_fried_egg", projected.default_combo_egg_component_code);
        assertEquals(new BigDecimal("16.00"), projected.base_price);
        assertEquals(3L, projected.station_id);
        item.default_combo_egg_component_code = null;
        var inherited = service.getCatalog(9L);
        assertNull(inherited.categories.get(0).items.get(0).default_combo_egg_component_code);
        assertNotEquals(explicit.content_hash, inherited.content_hash);
    }
}
