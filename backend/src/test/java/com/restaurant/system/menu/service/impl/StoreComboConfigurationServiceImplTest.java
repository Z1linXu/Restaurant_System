package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.combo.StoreComboComponent;
import com.restaurant.system.menu.combo.StoreComboComponentRepository;
import com.restaurant.system.menu.dto.MenuRevisionResponse;
import com.restaurant.system.menu.dto.StoreComboConfigurationUpdateRequest;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreComboConfigurationServiceImplTest {

    @Mock
    private StoreComboComponentRepository storeComboComponentRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private MenuItemOptionRepository menuItemOptionRepository;
    @Mock
    private MenuRevisionService menuRevisionService;

    private StoreComboConfigurationServiceImpl service;
    private List<StoreComboComponent> components;

    @BeforeEach
    void setUp() {
        service = new StoreComboConfigurationServiceImpl(
            storeComboComponentRepository,
            storeRepository,
            menuItemOptionRepository,
            menuRevisionService
        );
        components = new ArrayList<>(List.of(
            component(1L, 10L, "COMBO_EGG", "combo_tea_egg", true, 10),
            component(2L, 10L, "COMBO_EGG", "combo_fried_egg", true, 20),
            component(3L, 10L, "COMBO_SIDE", "combo_edamame", true, 10),
            component(4L, 10L, "COMBO_SIDE", "combo_shredded_potato", true, 20),
            component(5L, 10L, "COMBO_SIDE", "combo_cucumber_salad", true, 30),
            component(6L, 20L, "COMBO_EGG", "combo_tea_egg", true, 10),
            component(7L, 20L, "COMBO_EGG", "combo_fried_egg", false, 20),
            component(8L, 20L, "COMBO_SIDE", "combo_edamame", true, 10),
            component(9L, 20L, "COMBO_SIDE", "combo_shredded_potato", true, 20),
            component(10L, 20L, "COMBO_SIDE", "combo_cucumber_salad", true, 30)
        ));
        when(storeRepository.existsById(any())).thenReturn(true);
        when(storeComboComponentRepository.findAllByStoreIdOrdered(any())).thenAnswer(invocation -> {
            Long storeId = invocation.getArgument(0);
            return components.stream()
                .filter(component -> storeId.equals(component.store_id))
                .sorted(Comparator.comparing((StoreComboComponent component) -> component.component_group)
                    .thenComparing(component -> component.display_order)
                    .thenComparing(component -> component.id))
                .toList();
        });
        when(storeComboComponentRepository.findByStoreIdAndGroupAndCode(any(), any(), any())).thenAnswer(invocation -> {
            Long storeId = invocation.getArgument(0);
            String group = invocation.getArgument(1);
            String code = invocation.getArgument(2);
            return components.stream()
                .filter(component -> storeId.equals(component.store_id))
                .filter(component -> group.equals(component.component_group))
                .filter(component -> code.equals(component.component_code))
                .findFirst();
        });
        when(storeComboComponentRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<StoreComboComponent> saved = invocation.getArgument(0);
            List<StoreComboComponent> savedList = new ArrayList<>();
            AtomicLong ids = new AtomicLong(100L);
            for (StoreComboComponent component : saved) {
                if (component.id == null) {
                    component.id = ids.getAndIncrement();
                }
                components.removeIf(existing -> existing.id.equals(component.id));
                components.add(component);
                savedList.add(component);
            }
            return savedList;
        });
        when(menuRevisionService.getRevision(any())).thenAnswer(invocation -> revision(invocation.getArgument(0), 12L));
    }

    @Test
    void readReturnsStoreScopedCanonicalComponentsAndDefaultByDisplayOrder() {
        var response = service.getConfiguration(20L);

        assertEquals(20L, response.store_id);
        assertEquals(2, response.groups.size());
        assertEquals("combo_tea_egg", response.groups.get(0).default_component_code);
        assertFalse(response.groups.get(0).components.get(1).enabled);
        assertEquals("combo_edamame", response.groups.get(1).default_component_code);
    }

    @Test
    void updateCanDisableFriedEggForOneStoreWithoutChangingAnotherStore() {
        when(menuItemOptionRepository.findActiveByStoreIdOrdered(20L)).thenReturn(List.of(
            option(80L, 200L, "COMBO", "combo"),
            option(81L, 200L, "COMBO_EGG", "combo_tea_egg"),
            option(82L, 200L, "COMBO_EGG", "combo_fried_egg"),
            option(83L, 200L, "COMBO_SIDE", "combo_edamame")
        ));

        var request = update(
            toggle("COMBO_EGG", "combo_fried_egg", false),
            toggle("COMBO_SIDE", "combo_shredded_potato", false)
        );
        var response = service.updateConfiguration(20L, request);

        assertFalse(componentForStore(20L, "COMBO_EGG", "combo_fried_egg").enabled);
        assertTrue(componentForStore(10L, "COMBO_EGG", "combo_fried_egg").enabled);
        assertEquals("combo_tea_egg", response.groups.get(0).default_component_code);
        verify(menuRevisionService).lockStoresInOrder(List.of(20L));
        verify(menuRevisionService).incrementRevision(20L);
    }

    @Test
    void rejectsInvalidComponentCode() {
        var request = updateForStore(10L, toggle("COMBO_EGG", "combo_duck_egg", true));

        assertThrows(BusinessException.class, () -> service.updateConfiguration(10L, request));
    }

    @Test
    void rejectsZeroEnabledEggWhenComboItemExists() {
        componentForStore(10L, "COMBO_EGG", "combo_fried_egg").enabled = false;
        when(menuItemOptionRepository.findActiveByStoreIdOrdered(10L)).thenReturn(List.of(
            option(80L, 100L, "COMBO", "combo"),
            option(81L, 100L, "COMBO_EGG", "combo_tea_egg"),
            option(83L, 100L, "COMBO_SIDE", "combo_edamame")
        ));

        var request = updateForStore(10L, toggle("COMBO_EGG", "combo_tea_egg", false));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.updateConfiguration(10L, request));
        assertEquals("COMBO_EGG_CONFIGURATION_MISSING", exception.getMessage());
    }

    @Test
    void disabledComponentIsNotCatalogOrNewOrderEligible() {
        MenuItemOption friedEgg = option(82L, 200L, "COMBO_EGG", "combo_fried_egg");

        assertFalse(service.isCatalogOptionEnabled(20L, friedEgg));
        assertThrows(BusinessException.class, () -> service.requireOptionEnabledForNewSelection(20L, friedEgg));
    }

    @Test
    void enabledStoreComboComponentsAreNotMenuItemCatalogOptions() {
        MenuItemOption teaEgg = option(81L, 100L, "COMBO_EGG", "combo_tea_egg");

        assertFalse(service.isCatalogOptionEnabled(10L, teaEgg));
        service.requireOptionEnabledForNewSelection(10L, teaEgg);
    }

    @Test
    void unsupportedStoreConfiguredComponentIsRejectedForNewOrderSelection() {
        MenuItemOption unknownEgg = option(84L, 200L, "COMBO_EGG", "combo_duck_egg");

        assertFalse(service.isCatalogOptionEnabled(20L, unknownEgg));
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.requireOptionEnabledForNewSelection(20L, unknownEgg)
        );
        assertEquals("COMBO_COMPONENT_UNSUPPORTED", exception.getMessage());
    }

    @Test
    void snapshotSelectionsAreValidatedAgainstStoreComboComponents() {
        service.requireSnapshotEnabledForNewSelection(10L, "COMBO_EGG", "combo_tea_egg");

        BusinessException disabled = assertThrows(
            BusinessException.class,
            () -> service.requireSnapshotEnabledForNewSelection(20L, "COMBO_EGG", "combo_fried_egg")
        );
        assertEquals("COMBO_COMPONENT_DISABLED: combo_fried_egg", disabled.getMessage());

        BusinessException unsupported = assertThrows(
            BusinessException.class,
            () -> service.requireSnapshotEnabledForNewSelection(10L, "COMBO_SIDE", "combo_fries")
        );
        assertEquals("COMBO_COMPONENT_UNSUPPORTED", unsupported.getMessage());
    }

    private StoreComboConfigurationUpdateRequest update(StoreComboConfigurationUpdateRequest.ComponentUpdate... updates) {
        return updateForStore(20L, updates);
    }

    private StoreComboConfigurationUpdateRequest updateForStore(
        Long storeId,
        StoreComboConfigurationUpdateRequest.ComponentUpdate... updates
    ) {
        StoreComboConfigurationUpdateRequest request = new StoreComboConfigurationUpdateRequest();
        request.store_id = storeId;
        request.components = List.of(updates);
        return request;
    }

    private StoreComboConfigurationUpdateRequest.ComponentUpdate toggle(String group, String code, boolean enabled) {
        StoreComboConfigurationUpdateRequest.ComponentUpdate update = new StoreComboConfigurationUpdateRequest.ComponentUpdate();
        update.component_group = group;
        update.component_code = code;
        update.enabled = enabled;
        return update;
    }

    private StoreComboComponent component(Long id, Long storeId, String group, String code, boolean enabled, int order) {
        StoreComboComponent component = new StoreComboComponent();
        component.id = id;
        component.store_id = storeId;
        component.component_group = group;
        component.component_code = code;
        component.name_zh = code;
        component.name_en = code;
        component.enabled = enabled;
        component.display_order = order;
        component.created_at = LocalDateTime.now();
        component.updated_at = component.created_at;
        return component;
    }

    private StoreComboComponent componentForStore(Long storeId, String group, String code) {
        Optional<StoreComboComponent> component = components.stream()
            .filter(current -> storeId.equals(current.store_id))
            .filter(current -> group.equals(current.component_group))
            .filter(current -> code.equals(current.component_code))
            .findFirst();
        return component.orElseThrow();
    }

    private MenuItemOption option(Long id, Long itemId, String group, String code) {
        MenuItemOption option = new MenuItemOption();
        option.id = id;
        option.menu_item_id = itemId;
        option.option_group = group;
        option.option_type = "addon";
        option.option_code = code;
        option.name_zh = "套餐";
        option.name_en = "Combo";
        option.is_active = true;
        return option;
    }

    private MenuRevisionResponse revision(Long storeId, Long revision) {
        return new MenuRevisionResponse(
            storeId,
            1L,
            revision,
            LocalDateTime.now(),
            "menu-catalog-v3",
            "ca-qc-tax-2026-01",
            "menu-" + storeId + "-" + revision
        );
    }
}
