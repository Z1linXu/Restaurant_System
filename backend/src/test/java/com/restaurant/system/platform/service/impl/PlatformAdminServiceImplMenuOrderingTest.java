package com.restaurant.system.platform.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.platform.dto.CreateStoreFromTemplateRequest;
import com.restaurant.system.platform.entity.RestaurantTemplate;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.platform.repository.RestaurantTemplateRepository;
import com.restaurant.system.platform.repository.StoreKdsDisplayConfigRepository;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.DiningTableRepository;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
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

    @Test
    void stationCreateIncrementsTargetStoreRevision() {
        when(stationRepository.save(any(Station.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Station request = new Station();
        request.store_id = 4L;
        request.code = "HOT";
        request.name = "Hot Kitchen";

        service.saveStation(request);

        verify(menuRevisionService).incrementRevision(4L);
    }

    @Test
    void stationUpdateIncrementsStoreRevision() {
        Station existing = new Station();
        existing.id = 8L;
        existing.store_id = 4L;
        when(stationRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(stationRepository.save(any(Station.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Station request = new Station();
        request.id = 8L;
        request.store_id = 4L;
        request.code = "COLD";
        request.name = "Cold Kitchen";

        service.saveStation(request);

        verify(menuRevisionService).incrementRevision(4L);
    }

    @Test
    void stationMoveUsesSharedOrderedMultiStoreRevisionContract() {
        Station existing = new Station();
        existing.id = 8L;
        existing.store_id = 9L;
        when(stationRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(stationRepository.save(any(Station.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Station request = new Station();
        request.id = 8L;
        request.store_id = 2L;

        service.saveStation(request);

        verify(menuRevisionService).incrementRevisionsInOrder(List.of(9L, 2L));
    }

    @Test
    void stationFailureDoesNotIncrementRevision() {
        doThrow(new IllegalStateException("synthetic station persistence failure"))
            .when(stationRepository).save(any(Station.class));
        Station request = new Station();
        request.store_id = 4L;

        assertThrows(IllegalStateException.class, () -> service.saveStation(request));

        verify(menuRevisionService, never()).incrementRevision(any());
        verify(menuRevisionService, never()).incrementRevisionsInOrder(any());
    }

    @Test
    void templateMenuWritesIncrementOnlyTheNewTargetStoreRevision() {
        CreateStoreFromTemplateRequest request = new CreateStoreFromTemplateRequest();
        request.organization_id = 12L;
        request.name = "Synthetic Target";
        request.code = "SYNTHETIC_TARGET";
        request.template_id = 5L;
        Store savedStore = new Store();
        savedStore.id = 22L;
        when(storeRepository.save(any(Store.class))).thenReturn(savedStore);
        when(storeRepository.findById(22L)).thenReturn(Optional.of(savedStore));
        RestaurantTemplate template = new RestaurantTemplate();
        template.id = 5L;
        template.source_store_id = 1L;
        template.default_station_setup_json = """
            [{"code":"HOT","name":"Hot Kitchen","sort_order":1,"is_active":true}]
            """;
        template.default_menu_category_structure_json = """
            [{"code":"NOODLE","name_zh":"面","name_en":"Noodles","sort_order":1,"is_active":true}]
            """;
        when(restaurantTemplateRepository.findById(5L)).thenReturn(Optional.of(template));

        service.createStoreFromTemplate(request);

        verify(menuRevisionService).incrementRevision(22L);
        verify(menuRevisionService, never()).incrementRevision(1L);
    }
}
