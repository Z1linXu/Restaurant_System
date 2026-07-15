package com.restaurant.system.menu.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.OwnerMenuItemOrderingService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerMenuItemOrderingServiceImpl implements OwnerMenuItemOrderingService {

    private static final int SORT_STEP = 10;

    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuRevisionService menuRevisionService;

    public OwnerMenuItemOrderingServiceImpl(
        MenuCategoryRepository menuCategoryRepository,
        MenuItemRepository menuItemRepository,
        MenuRevisionService menuRevisionService
    ) {
        this.menuCategoryRepository = menuCategoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.menuRevisionService = menuRevisionService;
    }

    @Override
    @Transactional
    public List<MenuItem> reorder(Long storeId, Long categoryId, List<Long> itemIds) {
        MenuCategory category = menuCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new BusinessException("Menu category not found: " + categoryId));
        if (!storeId.equals(category.store_id)) {
            throw new BusinessException("Menu category does not belong to store " + storeId);
        }
        if (itemIds == null) {
            throw new BusinessException("Menu item reorder list is required");
        }
        if (itemIds.stream().anyMatch(id -> id == null)) {
            throw new BusinessException("Menu item id is required for reorder");
        }
        if (new HashSet<>(itemIds).size() != itemIds.size()) {
            throw new BusinessException("Menu item reorder list contains duplicate item ids");
        }

        List<MenuItem> categoryItems = menuItemRepository.findAllByStoreIdAndCategoryIdForUpdate(storeId, categoryId);
        Map<Long, MenuItem> itemsById = categoryItems.stream()
            .collect(Collectors.toMap(item -> item.id, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        if (itemIds.size() != categoryItems.size() || !itemsById.keySet().equals(new HashSet<>(itemIds))) {
            validateRequestedItems(storeId, categoryId, itemIds);
            throw new BusinessException("Menu item reorder list must include every item in the category exactly once");
        }

        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < itemIds.size(); index++) {
            MenuItem item = itemsById.get(itemIds.get(index));
            item.sort_order = (index + 1) * SORT_STEP;
            item.updated_at = now;
        }
        List<MenuItem> saved = menuItemRepository.saveAll(itemIds.stream().map(itemsById::get).toList());
        menuRevisionService.incrementRevision(storeId);
        Map<Long, MenuItem> savedById = saved.stream().collect(Collectors.toMap(item -> item.id, Function.identity()));
        return itemIds.stream().map(savedById::get).toList();
    }

    private void validateRequestedItems(Long storeId, Long categoryId, List<Long> itemIds) {
        Map<Long, MenuItem> requested = menuItemRepository.findAllById(itemIds).stream()
            .collect(Collectors.toMap(item -> item.id, Function.identity()));
        for (Long itemId : itemIds) {
            MenuItem item = requested.get(itemId);
            if (item == null) {
                throw new BusinessException("Menu item not found: " + itemId);
            }
            if (!storeId.equals(item.store_id)) {
                throw new BusinessException("Cannot reorder menu item from another store: " + itemId);
            }
            if (!categoryId.equals(item.category_id)) {
                throw new BusinessException("Cannot reorder menu item from another category: " + itemId);
            }
        }
    }
}
