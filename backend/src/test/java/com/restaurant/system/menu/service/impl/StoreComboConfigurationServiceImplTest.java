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
import com.restaurant.system.menu.combo.StoreComboGroup;
import com.restaurant.system.menu.combo.StoreComboGroupRepository;
import com.restaurant.system.menu.dto.MenuRevisionResponse;
import com.restaurant.system.menu.dto.StoreComboConfigurationUpdateRequest;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
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
    private StoreComboGroupRepository storeComboGroupRepository;
    @Mock
    private StoreComboComponentRepository storeComboComponentRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private MenuItemOptionRepository menuItemOptionRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private MenuRevisionService menuRevisionService;

    private StoreComboConfigurationServiceImpl service;
    private List<StoreComboGroup> groups;
    private List<StoreComboComponent> components;

    @BeforeEach
    void setUp() {
        service = new StoreComboConfigurationServiceImpl(
            storeComboGroupRepository,
            storeComboComponentRepository,
            storeRepository,
            menuItemOptionRepository,
            menuItemRepository,
            menuRevisionService
        );
        groups = new ArrayList<>(List.of(
            group(101L, 10L, "COMBO_EGG", "蛋类", "Egg", 10),
            group(102L, 10L, "COMBO_SIDE", "小菜", "Side", 20),
            group(201L, 20L, "COMBO_EGG", "蛋类", "Egg", 10),
            group(202L, 20L, "COMBO_SIDE", "小菜", "Side", 20)
        ));
        components = new ArrayList<>(List.of(
            component(1L, 101L, 10L, "COMBO_EGG", "combo_tea_egg", true, 10),
            component(2L, 101L, 10L, "COMBO_EGG", "combo_fried_egg", true, 20),
            component(3L, 102L, 10L, "COMBO_SIDE", "combo_edamame", true, 10),
            component(4L, 102L, 10L, "COMBO_SIDE", "combo_shredded_potato", true, 20),
            component(5L, 102L, 10L, "COMBO_SIDE", "combo_cucumber_salad", true, 30),
            component(6L, 201L, 20L, "COMBO_EGG", "combo_tea_egg", true, 10),
            component(7L, 201L, 20L, "COMBO_EGG", "combo_fried_egg", false, 20),
            component(8L, 202L, 20L, "COMBO_SIDE", "combo_edamame", true, 10),
            component(9L, 202L, 20L, "COMBO_SIDE", "combo_shredded_potato", true, 20),
            component(10L, 202L, 20L, "COMBO_SIDE", "combo_cucumber_salad", true, 30)
        ));
        when(storeRepository.existsById(any())).thenReturn(true);
        when(storeComboGroupRepository.findAllByStoreIdOrdered(any())).thenAnswer(invocation -> groupsFor(invocation.getArgument(0)));
        when(storeComboGroupRepository.findAllByStoreIdForUpdateOrdered(any())).thenAnswer(invocation -> groupsFor(invocation.getArgument(0)));
        when(storeComboGroupRepository.findAllByStoreIdIncludingArchivedOrdered(any())).thenAnswer(invocation -> groups.stream()
            .filter(group -> invocation.<Long>getArgument(0).equals(group.store_id))
            .sorted(Comparator.comparing((StoreComboGroup group) -> group.display_order).thenComparing(group -> group.id))
            .toList());
        when(storeComboGroupRepository.findByStoreIdAndGroupCode(any(), any())).thenAnswer(invocation -> {
            Long storeId = invocation.getArgument(0);
            String groupCode = invocation.getArgument(1);
            return groups.stream()
                .filter(group -> storeId.equals(group.store_id))
                .filter(group -> groupCode.equals(group.group_code))
                .filter(group -> group.archived_at == null)
                .findFirst();
        });
        when(storeComboGroupRepository.save(any())).thenAnswer(invocation -> {
            StoreComboGroup saved = invocation.getArgument(0);
            if (saved.id == null) {
                saved.id = 300L + groups.size();
                groups.add(saved);
            }
            groups.removeIf(existing -> saved.id.equals(existing.id));
            groups.add(saved);
            return saved;
        });
        when(storeComboGroupRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<StoreComboGroup> saved = invocation.getArgument(0);
            List<StoreComboGroup> savedList = new ArrayList<>();
            for (StoreComboGroup group : saved) {
                groups.removeIf(existing -> group.id != null && group.id.equals(existing.id));
                groups.add(group);
                savedList.add(group);
            }
            return savedList;
        });
        when(storeComboComponentRepository.findAllByStoreIdOrdered(any())).thenAnswer(invocation -> {
            Long storeId = invocation.getArgument(0);
            return components.stream()
                .filter(component -> storeId.equals(component.store_id))
                .sorted(Comparator.comparing((StoreComboComponent component) -> component.component_group)
                    .thenComparing(component -> component.display_order)
                    .thenComparing(component -> component.id))
                .toList();
        });
        when(storeComboComponentRepository.findActiveByStoreIdOrdered(any())).thenAnswer(invocation -> {
            Long storeId = invocation.getArgument(0);
            return components.stream()
                .filter(component -> storeId.equals(component.store_id))
                .filter(component -> component.archived_at == null)
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
                .filter(component -> component.archived_at == null)
                .findFirst();
        });
        when(storeComboComponentRepository.save(any())).thenAnswer(invocation -> {
            StoreComboComponent saved = invocation.getArgument(0);
            if (saved.id == null) {
                saved.id = 500L + components.size();
                components.add(saved);
            }
            components.removeIf(existing -> saved.id.equals(existing.id));
            components.add(saved);
            return saved;
        });
        when(storeComboComponentRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<StoreComboComponent> saved = invocation.getArgument(0);
            List<StoreComboComponent> savedList = new ArrayList<>();
            AtomicLong ids = new AtomicLong(100L);
            for (StoreComboComponent component : saved) {
                if (component.id == null) {
                    component.id = ids.getAndIncrement();
                }
                components.removeIf(existing -> existing.id != null && existing.id.equals(component.id));
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

    @Test
    void updateCanCreateDynamicDrinkGroupAndValidateSnapshotSelections() {
        when(menuItemOptionRepository.findActiveByStoreIdOrdered(10L)).thenReturn(List.of(
            option(80L, 100L, "COMBO", "combo")
        ));

        StoreComboConfigurationUpdateRequest request = new StoreComboConfigurationUpdateRequest();
        request.store_id = 10L;
        request.groups = new ArrayList<>();
        request.groups.add(groupUpdate("COMBO_EGG", "蛋类", "Egg", "EXACTLY_ONE",
            componentUpdate(1L, "COMBO_EGG", "combo_tea_egg", "卤蛋", "Tea Egg", true, 10, true),
            componentUpdate(2L, "COMBO_EGG", "combo_fried_egg", "煎蛋", "Fried Egg", true, 20, false)
        ));
        request.groups.add(groupUpdate("COMBO_SIDE", "小菜", "Side", "EXACTLY_ONE",
            componentUpdate(3L, "COMBO_SIDE", "combo_edamame", "毛豆", "Edamame", true, 10, true)
        ));
        StoreComboConfigurationUpdateRequest.GroupUpdate drink = groupUpdate(null, "饮料", "Drink", "OPTIONAL_ONE",
            componentUpdate(null, null, null, "可乐", "Coke", true, 10, true),
            componentUpdate(null, null, null, "雪碧", "Sprite", true, 20, false)
        );
        request.groups.add(drink);

        var response = service.updateConfiguration(10L, request);

        var drinkGroup = response.groups.stream()
            .filter(group -> "COMBO_DRINK".equals(group.group_code))
            .findFirst()
            .orElseThrow();
        assertEquals("OPTIONAL_ONE", drinkGroup.selection_rule);
        assertFalse(drinkGroup.required);
        assertEquals("combo_coke", drinkGroup.default_component_code);
        assertEquals(List.of("combo_coke", "combo_sprite"), drinkGroup.components.stream().map(component -> component.component_code).toList());
        service.requireSnapshotEnabledForNewSelection(10L, "COMBO_DRINK", "combo_coke");
        verify(menuRevisionService).incrementRevision(10L);
    }

    @Test
    void nonKitchenTaskComponentClearsSubmittedLinkedMenuItem() {
        when(menuItemOptionRepository.findActiveByStoreIdOrdered(10L)).thenReturn(List.of(
            option(80L, 100L, "COMBO", "combo")
        ));

        StoreComboConfigurationUpdateRequest request = new StoreComboConfigurationUpdateRequest();
        request.store_id = 10L;
        StoreComboConfigurationUpdateRequest.ComponentUpdate teaEgg =
            componentUpdate(1L, "COMBO_EGG", "combo_tea_egg", "卤蛋", "Tea Egg", true, 10, true);
        teaEgg.business_behavior = "NO_KITCHEN_TASK";
        teaEgg.linked_menu_item_id = 999L;
        request.groups = List.of(
            groupUpdate("COMBO_EGG", "蛋类", "Egg", "EXACTLY_ONE", teaEgg),
            groupUpdate("COMBO_SIDE", "小菜", "Side", "EXACTLY_ONE",
                componentUpdate(3L, "COMBO_SIDE", "combo_edamame", "毛豆", "Edamame", true, 10, true)
            )
        );

        service.updateConfiguration(10L, request);

        assertEquals(null, componentForStore(10L, "COMBO_EGG", "combo_tea_egg").linked_menu_item_id);
    }

    @Test
    void rejectsCrossStoreLinkedMenuItemMapping() {
        when(menuItemOptionRepository.findActiveByStoreIdOrdered(10L)).thenReturn(List.of(
            option(80L, 100L, "COMBO", "combo")
        ));
        when(menuItemRepository.findById(900L)).thenReturn(Optional.of(menuItem(900L, 20L, true)));

        StoreComboConfigurationUpdateRequest request = new StoreComboConfigurationUpdateRequest();
        request.store_id = 10L;
        StoreComboConfigurationUpdateRequest.ComponentUpdate teaEgg =
            componentUpdate(1L, "COMBO_EGG", "combo_tea_egg", "卤蛋", "Tea Egg", true, 10, true);
        teaEgg.business_behavior = "LINKED_MENU_ITEM";
        teaEgg.linked_menu_item_id = 900L;
        request.groups = List.of(
            groupUpdate("COMBO_EGG", "蛋类", "Egg", "EXACTLY_ONE", teaEgg),
            groupUpdate("COMBO_SIDE", "小菜", "Side", "EXACTLY_ONE",
                componentUpdate(3L, "COMBO_SIDE", "combo_edamame", "毛豆", "Edamame", true, 10, true)
            )
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.updateConfiguration(10L, request)
        );

        assertEquals("COMBO_COMPONENT_MAPPING_INVALID", exception.getMessage());
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

    private StoreComboConfigurationUpdateRequest.GroupUpdate groupUpdate(
        String groupCode,
        String nameZh,
        String nameEn,
        String selectionRule,
        StoreComboConfigurationUpdateRequest.ComponentUpdate... components
    ) {
        StoreComboConfigurationUpdateRequest.GroupUpdate update = new StoreComboConfigurationUpdateRequest.GroupUpdate();
        update.group_code = groupCode;
        update.name_zh = nameZh;
        update.name_en = nameEn;
        update.selection_rule = selectionRule;
        update.required = !"OPTIONAL_ONE".equals(selectionRule);
        update.enabled = true;
        update.display_order = 10;
        update.components = List.of(components);
        return update;
    }

    private StoreComboConfigurationUpdateRequest.ComponentUpdate componentUpdate(
        Long id,
        String group,
        String code,
        String nameZh,
        String nameEn,
        boolean enabled,
        int order,
        boolean isDefault
    ) {
        StoreComboConfigurationUpdateRequest.ComponentUpdate update = new StoreComboConfigurationUpdateRequest.ComponentUpdate();
        update.id = id;
        update.component_group = group;
        update.component_code = code;
        update.name_zh = nameZh;
        update.name_en = nameEn;
        update.enabled = enabled;
        update.display_order = order;
        update.is_default = isDefault;
        update.business_behavior = "NO_KITCHEN_TASK";
        return update;
    }

    private List<StoreComboGroup> groupsFor(Long storeId) {
        return groups.stream()
            .filter(group -> storeId.equals(group.store_id))
            .filter(group -> group.archived_at == null)
            .sorted(Comparator.comparing((StoreComboGroup group) -> group.display_order).thenComparing(group -> group.id))
            .toList();
    }

    private StoreComboGroup group(Long id, Long storeId, String code, String nameZh, String nameEn, int order) {
        StoreComboGroup group = new StoreComboGroup();
        group.id = id;
        group.store_id = storeId;
        group.group_code = code;
        group.name_zh = nameZh;
        group.name_en = nameEn;
        group.selection_rule = "EXACTLY_ONE";
        group.required = true;
        group.enabled = true;
        group.display_order = order;
        group.created_at = LocalDateTime.now();
        group.updated_at = group.created_at;
        return group;
    }

    private StoreComboComponent component(Long id, Long groupId, Long storeId, String group, String code, boolean enabled, int order) {
        StoreComboComponent component = new StoreComboComponent();
        component.id = id;
        component.group_id = groupId;
        component.store_id = storeId;
        component.component_group = group;
        component.component_code = code;
        component.name_zh = code;
        component.name_en = code;
        component.enabled = enabled;
        component.display_order = order;
        component.business_behavior = "NO_KITCHEN_TASK";
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

    private MenuItem menuItem(Long id, Long storeId, boolean active) {
        MenuItem item = new MenuItem();
        item.id = id;
        item.store_id = storeId;
        item.is_active = active;
        return item;
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
