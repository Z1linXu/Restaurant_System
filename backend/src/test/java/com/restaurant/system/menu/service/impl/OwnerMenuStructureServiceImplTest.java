package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.combo.StoreComboComponent;
import com.restaurant.system.menu.combo.StoreComboComponentRepository;
import com.restaurant.system.menu.dto.MenuCategoryUpsertRequest;
import com.restaurant.system.menu.dto.StationUpsertRequest;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerMenuStructureServiceImplTest {

    @Mock
    private MenuCategoryRepository menuCategoryRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private StationRepository stationRepository;
    @Mock
    private StoreComboComponentRepository storeComboComponentRepository;
    @Mock
    private MenuRevisionService menuRevisionService;

    private OwnerMenuStructureServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OwnerMenuStructureServiceImpl(
            menuCategoryRepository,
            menuItemRepository,
            stationRepository,
            storeComboComponentRepository,
            menuRevisionService
        );
        when(menuRevisionService.lockStoresInOrder(any())).thenReturn(List.of());
    }

    @Test
    void categoryDeactivateRejectsActiveMenuItems() {
        MenuCategory category = category();
        when(menuCategoryRepository.findById(4L)).thenReturn(Optional.of(category));
        when(menuItemRepository.countActiveByStoreIdAndCategoryId(10L, 4L)).thenReturn(1L);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.updateCategory(10L, 4L, categoryRequest(false))
        );

        assertEquals("CATEGORY_HAS_ACTIVE_ITEMS", exception.getMessage());
    }

    @Test
    void categoryDeleteRejectsAnyMenuItemReference() {
        MenuCategory category = category();
        when(menuCategoryRepository.findById(4L)).thenReturn(Optional.of(category));
        when(menuItemRepository.countByStoreIdAndCategoryId(10L, 4L)).thenReturn(1L);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.deleteCategory(10L, 4L)
        );

        assertEquals("CATEGORY_NOT_EMPTY", exception.getMessage());
    }

    @Test
    void stationDeactivateRejectsActiveMenuItemReferences() {
        Station station = station("NOODLE");
        when(stationRepository.findById(7L)).thenReturn(Optional.of(station));
        when(menuItemRepository.countActiveByStoreIdAndStationId(10L, 7L)).thenReturn(2L);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.updateStation(10L, 7L, stationRequest(false))
        );

        assertEquals("STATION_HAS_ACTIVE_ITEMS", exception.getMessage());
    }

    @Test
    void coldStationDeactivateRejectsEnabledLegacyComboSideRouting() {
        Station station = station("COLD");
        when(stationRepository.findById(7L)).thenReturn(Optional.of(station));
        when(menuItemRepository.countActiveByStoreIdAndStationId(10L, 7L)).thenReturn(0L);
        when(storeComboComponentRepository.findActiveByStoreIdOrdered(10L)).thenReturn(List.of(legacyComboSide()));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.updateStation(10L, 7L, stationRequest(false))
        );

        assertEquals("STATION_HAS_LEGACY_COMBO_SIDE_ROUTING", exception.getMessage());
    }

    private MenuCategory category() {
        MenuCategory category = new MenuCategory();
        category.id = 4L;
        category.store_id = 10L;
        category.code = "CATEGORY_NOODLE";
        category.name_zh = "面";
        category.name_en = "Noodle";
        category.sort_order = 10;
        category.is_active = true;
        category.created_at = LocalDateTime.now();
        category.updated_at = category.created_at;
        return category;
    }

    private MenuCategoryUpsertRequest categoryRequest(boolean enabled) {
        MenuCategoryUpsertRequest request = new MenuCategoryUpsertRequest();
        request.store_id = 10L;
        request.name_zh = "面";
        request.name_en = "Noodle";
        request.sort_order = 10;
        request.enabled = enabled;
        return request;
    }

    private Station station(String code) {
        Station station = new Station();
        station.id = 7L;
        station.store_id = 10L;
        station.code = code;
        station.name = code;
        station.name_zh = code;
        station.name_en = code;
        station.station_type = "KITCHEN";
        station.sort_order = 10;
        station.is_active = true;
        station.created_at = LocalDateTime.now();
        station.updated_at = station.created_at;
        return station;
    }

    private StationUpsertRequest stationRequest(boolean enabled) {
        StationUpsertRequest request = new StationUpsertRequest();
        request.store_id = 10L;
        request.name_zh = "厨房";
        request.name_en = "Kitchen";
        request.station_type = "KITCHEN";
        request.sort_order = 10;
        request.enabled = enabled;
        return request;
    }

    private StoreComboComponent legacyComboSide() {
        StoreComboComponent component = new StoreComboComponent();
        component.store_id = 10L;
        component.component_group = "COMBO_SIDE";
        component.component_code = "combo_edamame";
        component.enabled = true;
        component.business_behavior = "LEGACY_COMBO_SIDE_TASK";
        return component;
    }
}
