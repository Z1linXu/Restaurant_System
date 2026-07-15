package com.restaurant.system.platform.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.platform.repository.RestaurantTemplateRepository;
import com.restaurant.system.platform.repository.StoreKdsDisplayConfigRepository;
import com.restaurant.system.station.repository.DiningTableRepository;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformAdminServiceImplMenuOrderingTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private RestaurantTemplateRepository restaurantTemplateRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private StationRepository stationRepository;
    @Mock private DiningTableRepository diningTableRepository;
    @Mock private MenuCategoryRepository menuCategoryRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private MenuItemOptionRepository menuItemOptionRepository;
    @Mock private StoreKdsDisplayConfigRepository storeKdsDisplayConfigRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private MenuRevisionService menuRevisionService;

    private PlatformAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlatformAdminServiceImpl(
            organizationRepository,
            restaurantTemplateRepository,
            storeRepository,
            stationRepository,
            diningTableRepository,
            menuCategoryRepository,
            menuItemRepository,
            menuItemOptionRepository,
            storeKdsDisplayConfigRepository,
            userRepository,
            roleRepository,
            menuRevisionService
        );
    }

    @Test
    void newMenuItemIsAppendedToCategory() {
        when(menuItemRepository.findMaxSortOrder(1L, 7L)).thenReturn(40);
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> {
            MenuItem item = invocation.getArgument(0);
            item.id = 99L;
            return item;
        });
        MenuItem request = new MenuItem();
        request.store_id = 1L;
        request.category_id = 7L;
        request.name_zh = "新菜";

        MenuItem saved = service.saveMenuItem(request);

        assertEquals(50, saved.sort_order);
        verify(menuRevisionService).incrementRevision(1L);
    }
}
