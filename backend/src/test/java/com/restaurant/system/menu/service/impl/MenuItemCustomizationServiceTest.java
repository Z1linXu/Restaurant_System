package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.dto.ItemCustomizationConfiguration;
import com.restaurant.system.menu.dto.StoreComboConfigurationResponse;
import com.restaurant.system.menu.entity.ItemCustomizationConfigurationConverter;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuItemCustomizationService;
import com.restaurant.system.menu.service.MenuItemCustomizationService.Selection;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.menu.addon.StoreAddonOptionResolver;
import com.restaurant.system.station.repository.StationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MenuItemCustomizationServiceTest {
    private final MenuItemRepository items = mock(MenuItemRepository.class);
    private final MenuItemOptionRepository options = mock(MenuItemOptionRepository.class);
    private final StoreComboConfigurationService combos = mock(StoreComboConfigurationService.class);
    private final MenuRevisionService revisions = mock(MenuRevisionService.class);
    private final StoreAddonOptionResolver addons = mock(StoreAddonOptionResolver.class);
    private final StationRepository stations = mock(StationRepository.class);
    private final MenuItemCustomizationService service = new MenuItemCustomizationService(items, options, combos, revisions, addons, stations);

    @org.junit.jupiter.api.BeforeEach void canonicalAddonProjection() {
        when(addons.resolve(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void noConfigurationRetainsLegacySizeComboAndAddonBehavior() {
        MenuItem item = item("15.99");
        assertNull(service.configuration(item));
        assertNull(service.fixedComboDelta(item));
        assertDoesNotThrow(() -> service.validateSelection(item, List.of(new Selection("SIZE", "large", 1), new Selection("ADD_ON", "beef", 3))));
    }

    @Test void fixedTotalHasCorrectDeltaForEveryEligibleBasePriceWithoutChargingComponents() {
        for (String base : List.of("15.99", "16.99", "17.99")) {
            MenuItem item = item(base);
            item.customization_config = new ItemCustomizationConfiguration();
            item.customization_config.combo = new ItemCustomizationConfiguration.Combo();
            item.customization_config.combo.fixed_price = new BigDecimal("19.99");
            assertEquals(new BigDecimal("19.99"), item.base_price.add(service.fixedComboDelta(item)));
        }
    }

    @Test void proteinSelectionRequiresExactlyTwoDistinctOptionsAndUnitQuantities() {
        MenuItem item = configured("PROTEIN", 2, 2);
        assertDoesNotThrow(() -> service.validateSelection(item, List.of(new Selection("PROTEIN", "beef", 1), new Selection("PROTEIN", "shrimp", 1))));
        for (List<Selection> invalid : List.of(List.<Selection>of(), List.of(new Selection("PROTEIN", "beef", 1)),
            List.of(new Selection("PROTEIN", "beef", 1), new Selection("PROTEIN", "beef", 1)),
            List.of(new Selection("PROTEIN", "beef", 2)),
            List.of(new Selection("PROTEIN", "beef", 1), new Selection("PROTEIN", "shrimp", 1), new Selection("PROTEIN", "pork", 1)))) {
            assertThrows(BusinessException.class, () -> service.validateSelection(item, invalid));
        }
    }

    @Test void freeAddonIsLimitedToOneAcrossDuplicateRowsAndDoesNotAffectPaidAddonQuantity() {
        MenuItem item = item("15.99");
        item.customization_config = new ItemCustomizationConfiguration();
        item.customization_config.addon_max_quantities = Map.of("extra_noodles", 1);
        assertDoesNotThrow(() -> service.validateSelection(item, List.of(new Selection("ADD_ON", "extra_noodles", 1), new Selection("ADD_ON", "beef", 3))));
        assertThrows(BusinessException.class, () -> service.validateSelection(item, List.of(new Selection("ADD_ON", "extra_noodles", 2))));
        assertThrows(BusinessException.class, () -> service.validateSelection(item, List.of(new Selection("ADD_ON", "extra_noodles", 1), new Selection("ADD_ON", "extra_noodles", 1))));
    }

    @Test void defaultOptionMustBeAnActiveOptionOfThisItemAndCanBeChanged() {
        MenuItem item = configured("NOODLE_TYPE", 1, 1);
        when(options.findAllByMenuItemIdOrdered(item.id)).thenReturn(List.of(option("NOODLE_TYPE", "thin"), option("NOODLE_TYPE", "thick")));
        item.customization_config.option_groups.get(0).default_option_codes = List.of("thin");
        assertDoesNotThrow(() -> service.validateConfiguration(item));
        item.customization_config.option_groups.get(0).default_option_codes = List.of("thick");
        assertDoesNotThrow(() -> service.validateConfiguration(item));
        item.customization_config.option_groups.get(0).default_option_codes = List.of("foreign_option");
        assertThrows(BusinessException.class, () -> service.validateConfiguration(item));
    }

    @Test void storeOwnedUpdateLocksChecksScopeAndAdvancesRevision() {
        MenuItem item = item("15.99");
        when(items.findById(item.id)).thenReturn(Optional.of(item));
        service.updateConfiguration(item.id, item.store_id, new ItemCustomizationConfiguration());
        verify(revisions).lockStoresInOrder(List.of(item.store_id));
        verify(revisions).incrementRevision(item.store_id);
        verify(items).save(item);
        assertThrows(BusinessException.class, () -> service.updateConfiguration(item.id, 99L, new ItemCustomizationConfiguration()));
    }

    @Test void foreignOrDisabledComboGroupCannotBeSelectedAndGroupsAreItemSpecific() {
        MenuItem item = item("15.99");
        item.customization_config = new ItemCustomizationConfiguration();
        item.customization_config.combo = new ItemCustomizationConfiguration.Combo();
        item.customization_config.combo.group_codes = List.of("COMBO_APPETIZER");
        var group = new StoreComboConfigurationResponse.GroupResponse();
        group.group_code = "COMBO_APPETIZER"; group.enabled = true;
        var component = new StoreComboConfigurationResponse.ComponentResponse(); component.enabled = true;
        group.components = List.of(component);
        assertEquals(List.of(group), service.applicableComboGroups(item, List.of(group)));
        assertThrows(BusinessException.class, () -> service.applicableComboGroups(item, List.of()));
        group.enabled = false;
        assertThrows(BusinessException.class, () -> service.applicableComboGroups(item, List.of(group)));
    }

    @Test void typedConfigurationRoundTripsThroughTextAndFailsClosedOnUnknownSchemaFields() {
        MenuItem item = configured("NOODLE_TYPE", 1, 1);
        item.customization_config.option_groups.get(0).default_option_codes = List.of("thin");
        var converter = new ItemCustomizationConfigurationConverter();
        String json = converter.convertToDatabaseColumn(item.customization_config);
        assertEquals("thin", converter.convertToEntityAttribute(json).option_groups.get(0).default_option_codes.get(0));
        assertNull(converter.convertToEntityAttribute(null));
        assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("{\"unrecognized\":true}"));
    }

    @Test void componentIdsAreDurableAndOutsideLegacyNamespace() {
        assertEquals(-1_000_000_041L, MenuItemCustomizationService.componentOptionId(41L));
        assertNotEquals(-1L, MenuItemCustomizationService.componentOptionId(1L));
        assertThrows(BusinessException.class, () -> MenuItemCustomizationService.componentOptionId(0L));
    }

    @Test void freeLimitUsesCanonicalAddonIdentityAndRetainsZeroPrice() {
        MenuItem item = item("15.99"); item.customization_config = new ItemCustomizationConfiguration();
        item.customization_config.addon_max_quantities = Map.of("extra_noodles", 1);
        MenuItemOption raw = option("ADD_ON", "old_materialized_code"); raw.store_addon_id = 42L;
        MenuItemOption canonical = option("ADD_ON", "extra_noodles"); canonical.store_addon_id = 42L;
        canonical.store_addon_store_id = item.store_id; canonical.addon_eligible = true;
        canonical.price_delta = BigDecimal.ZERO;
        when(options.findAllByMenuItemIdOrdered(item.id)).thenReturn(List.of(raw));
        when(addons.resolve(List.of(raw))).thenReturn(List.of(canonical));
        assertDoesNotThrow(() -> service.validateConfiguration(item));
        assertEquals(BigDecimal.ZERO, canonical.price_delta);
        canonical.store_addon_store_id = 99L;
        assertThrows(BusinessException.class, () -> service.validateConfiguration(item));
    }

    @Test void componentOnlyReferenceIsAllowedButForeignInactiveOrForeignStationIsRejected() {
        MenuItem item = item("15.99"); item.customization_config = new ItemCustomizationConfiguration();
        item.customization_config.combo = new ItemCustomizationConfiguration.Combo();
        item.customization_config.combo.fixed_price = new BigDecimal("19.99");
        item.customization_config.combo.group_codes = List.of("COMBO_SIDE");
        var group = new StoreComboConfigurationResponse.GroupResponse(); group.group_code = "COMBO_SIDE"; group.enabled = true;
        var component = new StoreComboConfigurationResponse.ComponentResponse();
        component.enabled = true; component.business_behavior = "LINKED_MENU_ITEM"; component.linked_menu_item_id = 8L;
        group.components = List.of(component);
        var combo = new StoreComboConfigurationResponse(); combo.groups = List.of(group);
        when(combos.getConfiguration(item.store_id)).thenReturn(combo);
        var linked = item("0.00"); linked.id = 8L; linked.sellable = false; linked.is_active = true; linked.station_id = 33L;
        when(items.findById(8L)).thenReturn(Optional.of(linked));
        when(stations.findActiveStationByIdAndStoreId(33L, item.store_id)).thenReturn(new com.restaurant.system.station.entity.Station());
        assertDoesNotThrow(() -> service.validateConfiguration(item));
        linked.store_id = 99L;
        assertThrows(BusinessException.class, () -> service.validateConfiguration(item));
        linked.store_id = item.store_id; linked.is_active = false;
        assertThrows(BusinessException.class, () -> service.validateConfiguration(item));
        linked.is_active = true; linked.station_id = 44L;
        assertThrows(BusinessException.class, () -> service.validateConfiguration(item));
    }

    private MenuItem item(String price) { MenuItem item = new MenuItem(); item.id = 7L; item.store_id = 3L; item.base_price = new BigDecimal(price); return item; }
    private MenuItem configured(String code, int min, int max) {
        MenuItem item = item("19.99"); item.customization_config = new ItemCustomizationConfiguration();
        var group = new ItemCustomizationConfiguration.OptionGroup(); group.option_group = code; group.min_select = min; group.max_select = max;
        item.customization_config.option_groups = List.of(group); return item;
    }
    private MenuItemOption option(String group, String code) { var option = new MenuItemOption(); option.menu_item_id = 7L; option.option_group = group; option.option_code = code; option.is_active = true; return option; }
}
