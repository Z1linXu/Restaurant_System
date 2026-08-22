package com.restaurant.system.platform.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.restaurant.system.owner.master.ChainMasterMenuEntity;
import com.restaurant.system.owner.master.ChainMasterMenuRepository;
import com.restaurant.system.owner.master.ChainMasterMenuVersionEntity;
import com.restaurant.system.owner.master.ChainMasterMenuVersionRepository;
import com.restaurant.system.owner.provisioning.StoreMenuMasterMappingEntity;
import com.restaurant.system.owner.provisioning.StoreMenuMasterMappingRepository;
import com.restaurant.system.platform.dto.CreateStoreFromTemplateRequest;
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
import org.mockito.ArgumentCaptor;
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
    @Mock private ChainMasterMenuRepository chainMasterMenuRepository;
    @Mock private ChainMasterMenuVersionRepository chainMasterMenuVersionRepository;
    @Mock private StoreMenuMasterMappingRepository storeMenuMasterMappingRepository;

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
            menuRevisionService,
            chainMasterMenuRepository,
            chainMasterMenuVersionRepository,
            storeMenuMasterMappingRepository
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
    void newMenuItemForPhaseBProvisionedStoreCreatesStoreOnlyMasterMapping() {
        Store store = store(44L, 100L);
        store.store_kind = "VALIDATION_FIXTURE";
        store.lifecycle_status = "READY_FOR_REVIEW";
        store.provisioning_source = "PHASE_B_OWNER_PROVISIONING";
        store.provisioned_master_menu_key = "LANZHOU_CHAIN_MASTER_MENU";
        store.provisioned_master_menu_version = "v1";
        store.provisioned_master_menu_fingerprint_sha256 = "m".repeat(64);
        ChainMasterMenuEntity masterMenu = new ChainMasterMenuEntity();
        masterMenu.id = 66L;
        ChainMasterMenuVersionEntity masterVersion = new ChainMasterMenuVersionEntity();
        masterVersion.id = 77L;
        masterVersion.status = "PUBLISHED";
        masterVersion.fingerprint_sha256 = "m".repeat(64);

        when(menuItemRepository.findMaxSortOrder(44L, 7L)).thenReturn(40);
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> {
            MenuItem item = invocation.getArgument(0);
            item.id = 99L;
            return item;
        });
        when(storeRepository.findById(44L)).thenReturn(Optional.of(store));
        when(chainMasterMenuRepository.findByOrganizationAndKey(
            100L,
            "LANZHOU_CHAIN_MASTER_MENU"
        )).thenReturn(Optional.of(masterMenu));
        when(chainMasterMenuVersionRepository.findByMasterMenuAndVersionKey(66L, "v1"))
            .thenReturn(Optional.of(masterVersion));

        MenuItem request = new MenuItem();
        request.store_id = 44L;
        request.category_id = 7L;
        request.name_zh = "本店测试菜";

        service.saveMenuItem(request);

        ArgumentCaptor<StoreMenuMasterMappingEntity> mappingCaptor =
            ArgumentCaptor.forClass(StoreMenuMasterMappingEntity.class);
        verify(storeMenuMasterMappingRepository).save(mappingCaptor.capture());
        StoreMenuMasterMappingEntity mapping = mappingCaptor.getValue();
        assertEquals(44L, mapping.store_id);
        assertEquals(77L, mapping.master_menu_version_id);
        assertEquals("ITEM", mapping.entity_type);
        assertEquals(99L, mapping.local_entity_id);
        assertEquals("STORE_ONLY", mapping.origin);
        assertEquals("STORE_ONLY", mapping.mapping_status);
        assertNull(mapping.master_category_key);
        assertNull(mapping.master_product_key);
        assertNull(mapping.master_option_key);
        verify(menuRevisionService).incrementRevision(44L);
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
    void legacyTemplateStoreCreationFailsClosedUntilPhaseBProvisioning() {
        CreateStoreFromTemplateRequest request = new CreateStoreFromTemplateRequest();
        request.organization_id = 12L;
        request.name = "Synthetic Target";
        request.code = "SYNTHETIC_TARGET";
        request.template_id = 5L;

        com.restaurant.system.common.exception.BusinessException exception = assertThrows(
            com.restaurant.system.common.exception.BusinessException.class,
            () -> service.createStoreFromTemplate(request)
        );

        assertEquals(
            "LEGACY_PLATFORM_STORE_CREATION_DISABLED_USE_PHASE_B_PROVISIONING",
            exception.getMessage()
        );
        verify(storeRepository, never()).save(any(Store.class));
        verify(menuRevisionService, never()).incrementRevision(any());
    }

    @Test
    void directNewStoreSaveFailsClosedUntilPhaseBProvisioning() {
        Store request = new Store();
        request.organization_id = 12L;
        request.name = "Direct Store";
        request.code = "DIRECT_STORE";

        com.restaurant.system.common.exception.BusinessException exception = assertThrows(
            com.restaurant.system.common.exception.BusinessException.class,
            () -> service.saveStore(request)
        );

        assertEquals(
            "LEGACY_PLATFORM_STORE_CREATION_DISABLED_USE_PHASE_B_PROVISIONING",
            exception.getMessage()
        );
        verify(storeRepository, never()).save(any(Store.class));
    }

    @Test
    void part2ValidationFixtureCannotUseLegacyDirectActiveWriter() {
        Store target = store(44L, 100L);
        target.store_kind = "VALIDATION_FIXTURE";
        target.provisioning_source = "PHASE_B_OWNER_PROVISIONING";
        target.lifecycle_status = "READY_FOR_REVIEW";
        when(storeRepository.findById(44L)).thenReturn(Optional.of(target));

        Store request = store(44L, 100L);
        request.status = "active";

        com.restaurant.system.common.exception.BusinessException exception = assertThrows(
            com.restaurant.system.common.exception.BusinessException.class,
            () -> service.saveStore(request)
        );

        assertEquals("PHASE_B_PART2_ACTIVATION_COORDINATOR_REQUIRED", exception.getMessage());
        verify(storeRepository, never()).save(any(Store.class));
    }

    private Store store(Long storeId, Long organizationId) {
        Store store = new Store();
        store.id = storeId;
        store.organization_id = organizationId;
        store.name = "Store " + storeId;
        store.code = "STORE_" + storeId;
        store.status = "inactive";
        return store;
    }
}
