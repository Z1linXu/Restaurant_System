package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerMenuItemOrderingServiceImplTest {

    @Mock
    private MenuCategoryRepository menuCategoryRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private MenuRevisionService menuRevisionService;

    private OwnerMenuItemOrderingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OwnerMenuItemOrderingServiceImpl(
            menuCategoryRepository,
            menuItemRepository,
            menuRevisionService
        );
    }

    @Test
    void reordersCompleteCategoryAndRenumbersPositions() {
        MenuItem first = item(11L, 1L, 7L, 10);
        MenuItem second = item(12L, 1L, 7L, 20);
        stubCategory(1L, 7L);
        when(menuItemRepository.findAllByStoreIdAndCategoryIdForUpdate(1L, 7L))
            .thenReturn(List.of(first, second));
        when(menuItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<MenuItem> result = service.reorder(1L, 7L, List.of(12L, 11L));

        assertEquals(List.of(12L, 11L), result.stream().map(item -> item.id).toList());
        assertEquals(10, second.sort_order);
        assertEquals(20, first.sort_order);
        verify(menuRevisionService).incrementRevision(1L);
    }

    @Test
    void rejectsDuplicateItemIds() {
        stubCategory(1L, 7L);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.reorder(1L, 7L, List.of(11L, 11L))
        );

        assertTrue(exception.getMessage().contains("duplicate"));
    }

    @Test
    void rejectsOmittedCategoryItem() {
        MenuItem first = item(11L, 1L, 7L, 10);
        MenuItem second = item(12L, 1L, 7L, 20);
        stubCategory(1L, 7L);
        when(menuItemRepository.findAllByStoreIdAndCategoryIdForUpdate(1L, 7L))
            .thenReturn(List.of(first, second));
        when(menuItemRepository.findAllById(List.of(11L))).thenReturn(List.of(first));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.reorder(1L, 7L, List.of(11L))
        );

        assertTrue(exception.getMessage().contains("every item"));
    }

    @Test
    void rejectsItemFromAnotherCategory() {
        MenuItem first = item(11L, 1L, 7L, 10);
        MenuItem otherCategory = item(21L, 1L, 8L, 10);
        stubCategory(1L, 7L);
        when(menuItemRepository.findAllByStoreIdAndCategoryIdForUpdate(1L, 7L)).thenReturn(List.of(first));
        when(menuItemRepository.findAllById(List.of(21L))).thenReturn(List.of(otherCategory));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.reorder(1L, 7L, List.of(21L))
        );

        assertTrue(exception.getMessage().contains("another category"));
    }

    @Test
    void rejectsItemFromAnotherStore() {
        MenuItem first = item(11L, 1L, 7L, 10);
        MenuItem otherStore = item(31L, 2L, 7L, 10);
        stubCategory(1L, 7L);
        when(menuItemRepository.findAllByStoreIdAndCategoryIdForUpdate(1L, 7L)).thenReturn(List.of(first));
        when(menuItemRepository.findAllById(List.of(31L))).thenReturn(List.of(otherStore));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.reorder(1L, 7L, List.of(31L))
        );

        assertTrue(exception.getMessage().contains("another store"));
    }

    private void stubCategory(Long storeId, Long categoryId) {
        MenuCategory category = new MenuCategory();
        category.id = categoryId;
        category.store_id = storeId;
        when(menuCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    }

    private MenuItem item(Long id, Long storeId, Long categoryId, Integer sortOrder) {
        MenuItem item = new MenuItem();
        item.id = id;
        item.store_id = storeId;
        item.category_id = categoryId;
        item.sort_order = sortOrder;
        return item;
    }
}
