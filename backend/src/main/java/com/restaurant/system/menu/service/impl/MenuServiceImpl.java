package com.restaurant.system.menu.service.impl;

import com.restaurant.system.menu.dto.MenuCatalogResponse;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MenuServiceImpl implements MenuService {

    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemOptionRepository menuItemOptionRepository;

    public MenuServiceImpl(
        MenuCategoryRepository menuCategoryRepository,
        MenuItemRepository menuItemRepository,
        MenuItemOptionRepository menuItemOptionRepository
    ) {
        this.menuCategoryRepository = menuCategoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.menuItemOptionRepository = menuItemOptionRepository;
    }

    @Override
    public MenuCatalogResponse getCatalog(Long storeId) {
        List<MenuCategory> categories = menuCategoryRepository.findActiveByStoreId(storeId);
        List<MenuItem> items = menuItemRepository.findActiveByStoreId(storeId);
        List<Long> itemIds = items.stream().map(menuItem -> menuItem.id).toList();
        List<MenuItemOption> options = itemIds.isEmpty()
            ? List.of()
            : menuItemOptionRepository.findActiveByMenuItemIds(itemIds);

        Map<Long, List<MenuCatalogResponse.OptionResponse>> optionsByItemId = options.stream()
            .collect(Collectors.groupingBy(
                option -> option.menu_item_id,
                LinkedHashMap::new,
                Collectors.mapping(
                    option -> new MenuCatalogResponse.OptionResponse(
                        option.id,
                        option.option_type,
                        option.name_zh,
                        option.name_en,
                        option.price_delta
                    ),
                    Collectors.toList()
                )
            ));

        Map<Long, List<MenuCatalogResponse.ItemResponse>> itemsByCategoryId = new LinkedHashMap<>();
        for (MenuItem item : items) {
            itemsByCategoryId.computeIfAbsent(item.category_id, ignored -> new ArrayList<>())
                .add(new MenuCatalogResponse.ItemResponse(
                    item.id,
                    item.category_id,
                    item.station_id,
                    item.name_zh,
                    item.name_en,
                    item.sku,
                    item.item_type,
                    item.base_price,
                    item.is_sold_out,
                    optionsByItemId.getOrDefault(item.id, List.of())
                ));
        }

        List<MenuCatalogResponse.CategoryResponse> categoryResponses = categories.stream()
            .sorted(Comparator
                .comparing((MenuCategory category) -> category.sort_order == null ? Integer.MAX_VALUE : category.sort_order)
                .thenComparing(category -> category.id))
            .map(category -> new MenuCatalogResponse.CategoryResponse(
                category.id,
                category.code,
                category.name_zh,
                category.name_en,
                category.sort_order,
                itemsByCategoryId.getOrDefault(category.id, List.of())
            ))
            .toList();

        return new MenuCatalogResponse(storeId, categoryResponses);
    }
}
