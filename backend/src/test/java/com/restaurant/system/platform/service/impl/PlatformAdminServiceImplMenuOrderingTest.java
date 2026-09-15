package com.restaurant.system.platform.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
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
import com.restaurant.system.user.entity.User;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
    void storeReadsUseScopedRepositoryQueriesAndPreserveStoreIsolation() {
        Station station = new Station();
        station.id = 21L;
        station.store_id = 7L;
        station.sort_order = 2;
        when(stationRepository.findAllByStoreIdOrderByIdAsc(7L)).thenReturn(List.of(station));

        MenuCategory category = new MenuCategory();
        category.id = 31L;
        category.store_id = 7L;
        category.sort_order = 1;
        when(menuCategoryRepository.findAllByStoreIdOrderByIdAsc(7L)).thenReturn(List.of(category));

        MenuItem item = new MenuItem();
        item.id = 41L;
        item.store_id = 7L;
        item.category_id = 31L;
        item.sort_order = 1;
        when(menuItemRepository.findAllByStoreIdOrderByIdAsc(7L)).thenReturn(List.of(item));

        MenuItemOption option = new MenuItemOption();
        option.id = 51L;
        option.menu_item_id = 41L;
        when(menuItemOptionRepository.findAllByStoreIdOrderByIdAsc(7L)).thenReturn(List.of(option));

        User user = new User();
        user.setId(61L);
        user.setStore_id(7L);
        when(userRepository.findAllByStore_id(7L)).thenReturn(List.of(user));

        assertThat(service.getStations(7L)).containsExactly(station);
        assertThat(service.getMenuCategories(7L)).containsExactly(category);
        assertThat(service.getMenuItems(7L)).containsExactly(item);
        assertThat(service.getMenuItemOptions(7L)).containsExactly(option);
        assertThat(service.getUsers(7L)).containsExactly(user);

        verify(stationRepository).findAllByStoreIdOrderByIdAsc(7L);
        verify(menuCategoryRepository).findAllByStoreIdOrderByIdAsc(7L);
        verify(menuItemRepository).findAllByStoreIdOrderByIdAsc(7L);
        verify(menuItemOptionRepository).findAllByStoreIdOrderByIdAsc(7L);
        verify(userRepository).findAllByStore_id(7L);
        verify(stationRepository, never()).findAll();
        verify(menuCategoryRepository, never()).findAll();
        verify(menuItemRepository, never()).findAll();
        verify(menuItemOptionRepository, never()).findAll();
        verify(userRepository, never()).findAll();
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ADD_ON", " add_on ", " "})
    void platformCreateCannotBypassAddonCatalog(String group) {
        MenuItemOption request = addon(group);

        BusinessException error = assertThrows(BusinessException.class, () -> service.saveMenuItemOption(request));

        assertThat(error.getMessage()).contains("Store Add-on catalog", "reconciliation");
        verify(menuItemOptionRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ADD_ON", " add_on ", " "})
    void platformCannotDisguiseExistingAddonAsAnotherGroupOrMoveIt(String group) {
        MenuItemOption existing = addon(group);
        existing.id = 90L;
        when(menuItemOptionRepository.findById(90L)).thenReturn(Optional.of(existing));
        MenuItemOption request = addon("REMOVE");
        request.id = 90L;
        request.menu_item_id = 25L;
        request.option_type = "remove";
        request.name_zh = "替换";

        assertThrows(BusinessException.class, () -> service.saveMenuItemOption(request));

        assertEquals(14L, existing.menu_item_id);
        assertEquals("加煎蛋", existing.name_zh);
        verify(menuItemOptionRepository, never()).save(any());
    }

    @Test
    void platformCannotConvertNonAddonIntoCatalogAddon() {
        MenuItemOption existing = addon("REMOVE");
        existing.option_type = "remove";
        existing.id = 90L;
        when(menuItemOptionRepository.findById(90L)).thenReturn(Optional.of(existing));
        MenuItemOption request = addon("ADD_ON");
        request.id = 90L;

        assertThrows(BusinessException.class, () -> service.saveMenuItemOption(request));

        assertEquals("REMOVE", existing.option_group);
        verify(menuItemOptionRepository, never()).save(any());
    }

    @Test
    void platformNonAddonWriteStillUpdatesOwningStoreRevision() {
        MenuItemOption request = addon("REMOVE");
        request.option_type = "remove";
        MenuItem item = new MenuItem();
        item.id = 14L;
        item.store_id = 3L;
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("REMOVE", service.saveMenuItemOption(request).option_group);

        verify(menuRevisionService).incrementRevision(3L);
    }

    private MenuItemOption addon(String group) {
        MenuItemOption option = new MenuItemOption();
        option.menu_item_id = 14L;
        option.option_type = "addon";
        option.option_group = group;
        option.option_code = "fried_egg";
        option.name_zh = "加煎蛋";
        option.name_en = "Fried Egg";
        return option;
    }

    @Test
    void genericItemCreationCannotSetComboEggOverride() {
        MenuItem item = new MenuItem();
        item.store_id = 3L;
        item.default_combo_egg_component_code = "combo_fried_egg";

        assertThrows(BusinessException.class, () -> service.saveMenuItem(item));

        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void genericItemUpdateCannotChangeComboEggOverrideOrMoveItsStore() {
        MenuItem existing = new MenuItem();
        existing.id = 14L;
        existing.store_id = 3L;
        existing.default_combo_egg_component_code = "combo_fried_egg";
        when(menuItemRepository.findStoreIdById(14L)).thenReturn(Optional.of(3L));
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(existing));
        MenuItem request = new MenuItem();
        request.id = 14L;
        request.store_id = 3L;
        request.default_combo_egg_component_code = "combo_tea_egg";

        assertThrows(BusinessException.class, () -> service.saveMenuItem(request));
        request.default_combo_egg_component_code = null;
        request.store_id = 4L;
        assertThrows(BusinessException.class, () -> service.saveMenuItem(request));

        assertEquals(3L, existing.store_id);
        assertEquals("combo_fried_egg", existing.default_combo_egg_component_code);
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void genericItemUpdatePreservesOmittedComboEggOverride() {
        MenuItem existing = new MenuItem();
        existing.id = 14L;
        existing.store_id = 3L;
        existing.sort_order = 10;
        existing.default_combo_egg_component_code = "combo_fried_egg";
        when(menuItemRepository.findStoreIdById(14L)).thenReturn(Optional.of(3L));
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(existing));
        when(menuItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MenuItem request = new MenuItem();
        request.id = 14L;
        request.store_id = 3L;
        request.name_zh = "新名称";

        assertEquals("combo_fried_egg", service.saveMenuItem(request).default_combo_egg_component_code);
        verify(menuRevisionService).incrementRevision(3L);
    }

    @Test
    void genericItemMoveLocksBothStoresBeforeLoadingManagedItem() {
        MenuItem existing = new MenuItem();
        existing.id = 14L;
        existing.store_id = 9L;
        when(menuItemRepository.findStoreIdById(14L)).thenReturn(Optional.of(9L));
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(existing));
        when(menuItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MenuItem request = new MenuItem();
        request.id = 14L;
        request.store_id = 3L;

        assertEquals(3L, service.saveMenuItem(request).store_id);

        var order = inOrder(menuItemRepository, menuRevisionService);
        order.verify(menuItemRepository).findStoreIdById(14L);
        order.verify(menuRevisionService).lockStoresInOrder(List.of(3L, 9L));
        order.verify(menuItemRepository).findById(14L);
        verify(menuRevisionService).incrementRevisionsInOrder(List.of(9L, 3L));
    }

    @Test
    void genericItemMoveFailsIfItemMovedWhileWaitingForStoreLocks() {
        MenuItem existing = new MenuItem();
        existing.id = 14L;
        existing.store_id = 5L;
        when(menuItemRepository.findStoreIdById(14L)).thenReturn(Optional.of(9L));
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(existing));
        MenuItem request = new MenuItem();
        request.id = 14L;
        request.store_id = 3L;

        BusinessException error = assertThrows(BusinessException.class, () -> service.saveMenuItem(request));

        assertThat(error.getMessage()).contains("Store changed");
        assertEquals(5L, existing.store_id);
        verify(menuRevisionService).lockStoresInOrder(List.of(3L, 9L));
        verify(menuItemRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMBO", "combo", " COMBO "})
    void platformCannotCreateCanonicalComboThroughGenericOptionWriter(String group) {
        MenuItemOption request = addon(group);

        BusinessException error = assertThrows(BusinessException.class, () -> service.saveMenuItemOption(request));

        assertThat(error.getMessage()).contains("Combo Policy", "Pricing Rules");
        verify(menuItemOptionRepository, never()).save(any());
        verifyNoInteractions(menuItemRepository, menuRevisionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deactivate", "move", "convert", "convert_to_addon"})
    void platformRejectsSourceComboMutationBeforeChangingOptionOrItemOverride(String mutation) {
        MenuItemOption existing = addon("COMBO");
        existing.id = 90L;
        existing.option_code = "combo";
        existing.name_zh = "套餐";
        existing.name_en = "Combo";
        existing.price_delta = new java.math.BigDecimal("5.00");
        existing.is_active = true;
        when(menuItemOptionRepository.findById(90L)).thenReturn(Optional.of(existing));
        MenuItemOption request = addon("COMBO");
        request.id = 90L;
        request.option_code = "combo";
        request.name_zh = "套餐";
        request.name_en = "Combo";
        request.is_active = true;
        switch (mutation) {
            case "deactivate" -> request.is_active = false;
            case "move" -> request.menu_item_id = 25L;
            case "convert" -> {
                request.option_group = "REMOVE";
                request.option_type = "remove";
                request.option_code = "no_cilantro";
            }
            case "convert_to_addon" -> request.option_group = "ADD_ON";
            default -> throw new AssertionError("Unexpected test mutation");
        }

        BusinessException error = assertThrows(BusinessException.class, () -> service.saveMenuItemOption(request));

        assertThat(error.getMessage()).contains("Combo Policy");
        assertEquals(14L, existing.menu_item_id);
        assertEquals("COMBO", existing.option_group);
        assertEquals("combo", existing.option_code);
        assertEquals("套餐", existing.name_zh);
        assertEquals(new java.math.BigDecimal("5.00"), existing.price_delta);
        assertEquals(true, existing.is_active);
        verify(menuItemOptionRepository, never()).save(any());
        // Denial occurs before any item lookup/write, so the owning item's override stays intact.
        verifyNoInteractions(menuItemRepository, menuRevisionService);
    }

    @Test
    void platformCannotConvertAnotherGroupIntoCanonicalCombo() {
        MenuItemOption existing = addon("REMOVE");
        existing.id = 90L;
        existing.option_type = "remove";
        existing.option_code = "no_cilantro";
        existing.is_active = true;
        when(menuItemOptionRepository.findById(90L)).thenReturn(Optional.of(existing));
        MenuItemOption request = addon("COMBO");
        request.id = 90L;

        BusinessException error = assertThrows(BusinessException.class, () -> service.saveMenuItemOption(request));

        assertThat(error.getMessage()).contains("Combo Policy");
        assertEquals("REMOVE", existing.option_group);
        assertEquals("no_cilantro", existing.option_code);
        assertEquals(true, existing.is_active);
        verify(menuItemOptionRepository, never()).save(any());
        verifyNoInteractions(menuItemRepository, menuRevisionService);
    }

    @Test
    void platformExplicitRemoveWithComboCodeAndNamesKeepsItsOwnSemantics() {
        MenuItemOption existing = addon("REMOVE");
        existing.id = 90L;
        existing.option_code = "combo";
        existing.name_zh = "套餐";
        existing.name_en = "Combo";
        when(menuItemOptionRepository.findById(90L)).thenReturn(Optional.of(existing));
        MenuItem item = new MenuItem();
        item.id = 14L;
        item.store_id = 3L;
        item.default_combo_egg_component_code = "combo_fried_egg";
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MenuItemOption request = addon("REMOVE");
        request.id = 90L;
        request.option_code = "combo";
        request.name_zh = "套餐";
        request.name_en = "Combo";
        request.is_active = false;

        MenuItemOption saved = service.saveMenuItemOption(request);

        assertEquals("REMOVE", saved.option_group);
        assertEquals(false, saved.is_active);
        assertEquals("combo_fried_egg", item.default_combo_egg_component_code);
        verify(menuRevisionService).incrementRevision(3L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void platformStillProtectsLegacyComboUpcharge(String group) {
        MenuItemOption request = addon(group);
        request.option_code = "combo";
        request.name_zh = "套餐";
        request.name_en = "Combo";

        BusinessException error = assertThrows(BusinessException.class, () -> service.saveMenuItemOption(request));

        assertThat(error.getMessage()).contains("Combo Policy");
        verify(menuItemOptionRepository, never()).save(any());
    }
}
