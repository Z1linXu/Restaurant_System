package com.restaurant.system.menu.template;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.dto.MenuRevisionResponse;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.template.TemplateMenuModels.Group;
import com.restaurant.system.menu.template.TemplateMenuModels.Groups;
import com.restaurant.system.menu.template.TemplateMenuModels.Option;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.printing.routing.TemplateRoutingService;
import com.restaurant.system.template.bootstrap.TemplateTestBootstrap;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class TemplateMenuServiceTest {

    private final com.restaurant.system.menu.repository.MenuCategoryRepository categories = mock(com.restaurant.system.menu.repository.MenuCategoryRepository.class);
    private final MenuItemRepository items = mock(MenuItemRepository.class);
    private final MenuItemOptionRepository options = mock(MenuItemOptionRepository.class);
    private final TemplateItemOptionGroupRepository groups = mock(TemplateItemOptionGroupRepository.class);
    private final TemplateStoreConfigurationRepository configs = mock(TemplateStoreConfigurationRepository.class);
    private final MenuRevisionService revisions = mock(MenuRevisionService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Long, MenuItem> itemRows = new HashMap<>();
    private final Map<Long, MenuItemOption> optionRows = new LinkedHashMap<>();
    private final Map<Long, TemplateItemOptionGroup> groupRows = new LinkedHashMap<>();
    private final Map<Long, TemplateStoreConfiguration> configRows = new HashMap<>();
    private final AtomicLong optionSequence = new AtomicLong(100);
    private final AtomicLong groupSequence = new AtomicLong(1);
    private final AtomicLong revision = new AtomicLong(1);
    private TemplateMenuService service;

    @BeforeEach
    void setUp() {
        when(categories.findById(anyLong())).thenAnswer(call -> {
            long id=call.getArgument(0);var row=new com.restaurant.system.menu.entity.MenuCategory();
            row.id=id;row.store_id=id/1000L;row.code="SYNTHETIC";return Optional.of(row);
        });
        itemRows.put(10L, item(10L, 1L));
        itemRows.put(20L, item(20L, 1L));
        itemRows.put(30L, item(30L, 2L));
        when(items.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(itemRows.get(call.getArgument(0))));
        when(items.save(any())).thenAnswer(call -> {
            MenuItem row = call.getArgument(0);
            itemRows.put(row.id, row);
            return row;
        });
        when(options.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(optionRows.get(call.getArgument(0))));
        when(options.findAllByMenuItemIdOrdered(anyLong())).thenAnswer(call -> optionRows.values().stream()
            .filter(row -> row.menu_item_id.equals(call.<Long>getArgument(0)))
            .sorted(Comparator.comparing((MenuItemOption row) -> row.sort_order == null ? 0 : row.sort_order).thenComparing(row -> row.id)).toList());
        when(options.save(any())).thenAnswer(call -> {
            MenuItemOption row = call.getArgument(0);
            if (row.id == null) row.id = optionSequence.incrementAndGet();
            optionRows.put(row.id, row);
            return row;
        });
        when(groups.forItem(anyLong())).thenAnswer(call -> groupRows.values().stream()
            .filter(row -> row.menu_item_id.equals(call.<Long>getArgument(0)))
            .sorted(Comparator.comparingInt((TemplateItemOptionGroup row) -> row.sort_order).thenComparing(row -> row.id)).toList());
        when(groups.save(any())).thenAnswer(call -> {
            TemplateItemOptionGroup row = call.getArgument(0);
            if (row.id == null) row.id = groupSequence.incrementAndGet();
            groupRows.put(row.id, row);
            return row;
        });
        doAnswer(call -> { groupRows.remove(call.<TemplateItemOptionGroup>getArgument(0).id); return null; }).when(groups).delete(any());
        when(configs.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(configRows.get(call.getArgument(0))));
        when(configs.save(any())).thenAnswer(call -> {
            TemplateStoreConfiguration row = call.getArgument(0);
            configRows.put(row.store_id, row);
            return row;
        });
        when(revisions.getRevision(anyLong())).thenAnswer(call -> new MenuRevisionResponse(call.getArgument(0), 1L,
            revision.get(), LocalDateTime.of(2026, 10, 31, 0, 0), "test", "ca-qc-tax-2026-01", "test"));
        doAnswer(call -> { revision.incrementAndGet(); return null; }).when(revisions).incrementRevision(anyLong());
        service = new TemplateMenuService(items, options, groups, configs, revisions, mapper,
            new MockEnvironment().withProperty("app.template.enabled", "true"), categories);
    }

    @Test
    void libraryCopiesHaveIndependentOptionIdsDefaultsAndPricesPerItem() {
        Groups source = definition("spicy", "SPICY_LEVEL", "SINGLE", true,
            option("mild", "Mild", "0.00", true), option("hot", "Hot", "1.50", false));
        service.saveLibrary(1L, source);
        Groups a = service.saveItem(1L, 10L, service.library(1L));
        Groups b = service.saveItem(1L, 20L, service.library(1L));
        assertNotEquals(a.groups.get(0).options.get(0).id, b.groups.get(0).options.get(0).id);
        assertNotEquals(a.groups.get(0).options.get(1).id, b.groups.get(0).options.get(1).id);
        a.groups.get(0).options.get(1).price_delta = new BigDecimal("2.25");
        a.groups.get(0).options.get(0).is_default = false;
        a.groups.get(0).options.get(1).is_default = true;
        a.groups.get(0).default_option_ids.clear();
        service.saveItem(1L, 10L, a);

        Groups unchanged = service.getItem(1L, 20L);
        assertEquals(new BigDecimal("1.50"), unchanged.groups.get(0).options.get(1).price_delta);
        assertTrue(unchanged.groups.get(0).options.get(0).is_default);
        assertFalse(unchanged.groups.get(0).options.get(1).is_default);
        assertEquals(new BigDecimal("1.50"), service.library(1L).groups.get(0).options.get(1).price_delta);
        assertEquals(new BigDecimal("2.25"), service.getItem(1L, 10L).groups.get(0).options.get(1).price_delta);
    }

    @Test
    void itemOptionsAndSourceCopiesRejectForeignStoreIdentity() {
        assertThrows(ForbiddenException.class, () -> service.saveItem(1L, 30L, new Groups()));
        Groups foreign = service.saveItem(2L, 30L, definition("sauce", "CUSTOM", "SINGLE", false,
            option("garlic", "Garlic", "0.00", false)));
        Option copy = option("garlic", "Garlic", "0.00", false);
        copy.source_option_id = foreign.groups.get(0).options.get(0).id;
        assertThrows(ForbiddenException.class, () -> service.saveItem(1L, 10L, definition("sauce", "CUSTOM", "SINGLE", false, copy)));
        assertFalse(itemRows.get(10L).template_option_groups_enabled);
    }

    @Test
    void existingOptionIdsCannotBeBorrowedFromAnotherItemOrGroup() {
        Groups a = service.saveItem(1L, 10L, definition("sauce", "CUSTOM", "SINGLE", false,
            option("garlic", "Garlic", "0.00", false)));
        assertCode("TEMPLATE_OPTION_SCOPE_MISMATCH", () -> service.saveItem(1L, 20L, a));
        a.groups.get(0).group_instance_code = "other_group";
        assertCode("TEMPLATE_OPTION_SCOPE_MISMATCH", () -> service.saveItem(1L, 10L, a));
    }

    @Test
    void requiredSingleSelectionValidatesAbsenceCardinalityAndQuantity() {
        Groups saved = service.saveItem(1L, 10L, definition("sauce", "CUSTOM", "SINGLE", true,
            option("garlic", "Garlic", "0.00", true), option("pepper", "Pepper", "0.50", false)));
        Option first = saved.groups.get(0).options.get(0);
        Option second = saved.groups.get(0).options.get(1);
        assertCode("TEMPLATE_GROUP_SELECTION_INVALID", () -> service.validateSelection(1L, request(10L)));
        assertCode("TEMPLATE_GROUP_SELECTION_INVALID", () -> service.validateSelection(1L, request(10L, selected(first, 1), selected(second, 1))));
        assertCode("TEMPLATE_GROUP_SELECTION_INVALID", () -> service.validateSelection(1L, request(10L, selected(first, 2))));
        assertDoesNotThrow(() -> service.validateSelection(1L, request(10L, selected(first, 1))));
    }

    @Test
    void disabledGroupsAndOptionsCannotBeSubmitted() {
        Groups saved = service.saveItem(1L, 10L, definition("sauce", "CUSTOM", "SINGLE", false,
            option("garlic", "Garlic", "0.00", false)));
        Option first = saved.groups.get(0).options.get(0);
        saved.groups.get(0).is_active = false;
        service.saveItem(1L, 10L, saved);
        assertCode("TEMPLATE_GROUP_DISABLED", () -> service.validateSelection(1L, request(10L, selected(first, 1))));
        saved.groups.get(0).is_active = true;
        saved.groups.get(0).options.get(0).is_active = false;
        service.saveItem(1L, 10L, saved);
        assertCode("TEMPLATE_OPTION_UNAVAILABLE", () -> service.validateSelection(1L, request(10L, selected(first, 1))));
    }

    @Test
    void defaultsCannotBeInactiveOrMultiplySelectedInSingleGroup() {
        Option disabled = option("garlic", "Garlic", "0.00", true);
        disabled.is_active = false;
        assertCode("TEMPLATE_DEFAULT_INVALID", () -> service.saveItem(1L, 10L,
            definition("sauce", "CUSTOM", "SINGLE", false, disabled)));
        assertCode("TEMPLATE_DEFAULT_INVALID", () -> service.saveItem(1L, 10L,
            definition("sauce", "CUSTOM", "SINGLE", false, option("a", "A", "0.00", true), option("b", "B", "0.00", true))));
    }

    @Test
    void ordinaryMultipleSelectionHasNoNewBusinessMaximum() {
        List<Option> definitions = new ArrayList<>();
        for (int index = 0; index < 35; index++) definitions.add(option("extra_" + index, "Extra " + index, "0.25", false));
        Groups saved = service.saveItem(1L, 10L, definition("extras", "ADD_ON", "MULTIPLE", true, definitions.toArray(Option[]::new)));
        CreateOrderItemRequest request = request(10L);
        saved.groups.get(0).options.forEach(option -> request.options.add(selected(option, 2)));
        assertDoesNotThrow(() -> service.validateSelection(1L, request));
        assertEquals(35, request.options.size());
    }

    @Test
    void technicalQuantityAndDuplicateIdentityProtectionRemain() {
        Groups saved = service.saveItem(1L, 10L, definition("extras", "ADD_ON", "MULTIPLE", false,
            option("extra", "Extra", "0.25", false)));
        Option option = saved.groups.get(0).options.get(0);
        assertCode("TEMPLATE_OPTION_INVALID", () -> service.validateSelection(1L, request(10L, selected(option, 1000))));
        assertCode("TEMPLATE_OPTION_INVALID", () -> service.validateSelection(1L, request(10L, selected(option, 1), selected(option, 1))));
    }

    @Test
    void staleOrForgedOptionAndBasePriceAreRejectedByServerLeaves() {
        Groups saved = service.saveItem(1L, 10L, definition("extras", "ADD_ON", "MULTIPLE", false,
            option("extra", "Extra", "2.00", false)));
        Option leaf = saved.groups.get(0).options.get(0);
        CreateOrderItemOptionRequest stale = selected(leaf, 1);
        stale.option_price_snapshot = new BigDecimal("0.01");
        assertCode("TEMPLATE_OPTION_PRICE_CHANGED", () -> service.validateSelection(1L, request(10L, stale)));
        CreateOrderItemRequest base = request(10L, selected(leaf, 1));
        base.unit_price_snapshot = new BigDecimal("0.01");
        assertCode("MENU_PRICE_CHANGED", () -> service.validateSelection(1L, base));
        CreateOrderItemOptionRequest forged = selected(leaf, 1);
        forged.option_code_snapshot = "another_ingredient";
        assertCode("TEMPLATE_OPTION_CODE_CHANGED", () -> service.validateSelection(1L, request(10L, forged)));
    }

    @Test
    void invalidStationOrCrossStoreSelectionDoesNotPassTemplateValidation() {
        service.saveItem(1L, 10L, new Groups());
        CreateOrderItemRequest request = request(10L);
        request.station_id_snapshot = 9999L;
        assertCode("TEMPLATE_STATION_MISMATCH", () -> service.validateSelection(1L, request));
        assertThrows(ForbiddenException.class, () -> service.validateSelection(2L, request(10L)));
    }

    @Test
    void emptyGroupsMaterializeQuickAddMetadataAndRetainInactiveHistoricalLeaves() {
        Groups existing = service.saveItem(1L, 10L, definition("sauce", "CUSTOM", "SINGLE", false,
            option("garlic", "Garlic", "0.25", true)));
        long oldId = existing.groups.get(0).options.get(0).id;
        Groups noGroups = service.saveItem(1L, 10L, new Groups());
        assertTrue(service.manages(itemRows.get(10L)));
        assertTrue(noGroups.groups.isEmpty());
        assertTrue(groupRows.isEmpty());
        assertFalse(optionRows.get(oldId).is_active);
        assertEquals(new BigDecimal("0.25"), optionRows.get(oldId).price_delta);
        assertDoesNotThrow(() -> service.validateSelection(1L, request(10L)));
    }

    @Test
    void legacyAbsentGroupMetadataKeepsLegacyValidationResponsibility() {
        CreateOrderItemRequest legacy = request(10L);
        legacy.unit_price_snapshot = new BigDecimal("0.01");
        assertFalse(service.manages(itemRows.get(10L)));
        assertDoesNotThrow(() -> service.validateSelection(1L, legacy));
        verify(options, never()).findAllByMenuItemIdOrdered(anyLong());
        verify(groups, never()).forItem(anyLong());
    }

    @Test
    void freeRemoveAndNormalPricePrecisionConstraintsArePreserved() {
        assertCode("REMOVE_MUST_BE_FREE", () -> service.saveItem(1L, 10L,
            definition("remove", "REMOVE", "MULTIPLE", false, option("onion", "No onion", "1.00", false))));
        assertCode("TEMPLATE_OPTION_INVALID", () -> service.saveItem(1L, 10L,
            definition("sauce", "CUSTOM", "SINGLE", false, option("garlic", "Garlic", "0.001", false))));
        assertCode("TEMPLATE_OPTION_INVALID", () -> service.saveItem(1L, 10L,
            definition("sauce", "CUSTOM", "SINGLE", false, option("garlic", "Garlic", "-1.00", false))));
    }

    @Test
    void groupAndOptionOrderingAreReturnedFromPersistedMetadata() {
        Groups request = definition("later", "CUSTOM", "SINGLE", false,
            option("second", "Second", "0.00", false), option("first", "First", "0.00", false));
        request.groups.get(0).sort_order = 10;
        request.groups.get(0).options.get(0).sort_order = 20;
        request.groups.get(0).options.get(1).sort_order = 0;
        Group before = definition("before", "REMOVE", "MULTIPLE", false).groups.get(0);
        before.sort_order = 0;
        request.groups.add(before);
        Groups result = service.saveItem(1L, 10L, request);
        assertEquals(List.of("before", "later"), result.groups.stream().map(value -> value.group_instance_code).toList());
        assertEquals(List.of("first", "second"), result.groups.get(1).options.stream().map(value -> value.option_code).toList());
    }

    @Test
    void presetLibraryIsEmptyOfCustomerOptionsAndLibraryWritesDropRuntimeIdentities() {
        Groups presets = service.library(1L);
        assertEquals(List.of("SPICY_LEVEL", "ADD_ON", "REMOVE", "SIZE", "COMBO"), presets.groups.stream().map(group -> group.kind).toList());
        assertTrue(presets.groups.stream().allMatch(group -> group.options.isEmpty()));
        Groups request = definition("sauce", "CUSTOM", "SINGLE", false, option("garlic", "Garlic", "0.00", false));
        request.groups.get(0).options.get(0).id = 999L;
        request.groups.get(0).options.get(0).source_option_id = 888L;
        Groups saved = service.saveLibrary(1L, request);
        assertNull(saved.groups.get(0).options.get(0).id);
        assertNull(saved.groups.get(0).options.get(0).source_option_id);
        assertTrue(configRows.get(1L).routing_json.contains("\"enabled\":false"));
    }

    @Test
    void templateFeatureDisabledDoesNotManageLegacyItemsOrWriteNewGroups() {
        TemplateMenuService disabled = new TemplateMenuService(items, options, groups, configs, revisions, mapper, new MockEnvironment(), categories);
        assertFalse(disabled.manages(itemRows.get(10L)));
        assertCode("TEMPLATE_FEATURE_DISABLED", () -> disabled.saveItem(1L, 10L, new Groups()));
        verify(items, never()).save(any());
    }

    @Test
    void unknownOrNegativeItemCannotUseTrustedLookingCachedSnapshot() {
        var request=new com.restaurant.system.order.dto.CreateOrderItemRequest();request.menu_item_id=9999L;
        request.quantity=1;request.unit_price_snapshot=new BigDecimal("0.01");request.station_id_snapshot=100L;
        assertCode("TEMPLATE_ITEM_NOT_FOUND",()->service.validateSelection(1L,request));
        request.menu_item_id=-1L;assertCode("TEMPLATE_ITEM_NOT_FOUND",()->service.validateSelection(1L,request));
    }

    @Test
    void categoryForgeryCannotSkipRequiredKitchenProduction() {
        service.saveItem(1L,10L,new Groups());
        var request=new com.restaurant.system.order.dto.CreateOrderItemRequest();request.menu_item_id=10L;request.quantity=1;
        request.unit_price_snapshot=new BigDecimal("10.00");request.station_id_snapshot=100L;request.category_code_snapshot="DRINK";
        assertCode("TEMPLATE_CATEGORY_MISMATCH",()->service.validateSelection(1L,request));
    }

    @Test
    void bootstrapRefusesWrongProfileBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MockEnvironment environment = bootstrapEnvironment();
        environment.setActiveProfiles("production");
        assertThrows(IllegalStateException.class, () -> bootstrap(jdbc, environment).run(null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void bootstrapRefusesAnyOtherDatabaseWithoutWriting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("select current_database()", String.class)).thenReturn("restaurant_production");
        assertThrows(IllegalStateException.class, () -> bootstrap(jdbc, bootstrapEnvironment()).run(null));
        verify(jdbc, never()).execute(anyString());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void bootstrapRefusesMissingSecretsAndOrdinarySeederBeforeMutations() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("select current_database()", String.class)).thenReturn("restaurant_template_test");
        MockEnvironment missing = bootstrapEnvironment();
        missing.setProperty("TEMPLATE_TEST_OWNER_PASSWORD", "");
        assertThrows(IllegalStateException.class, () -> bootstrap(jdbc, missing).run(null));
        MockEnvironment unsafe = bootstrapEnvironment().withProperty("app.seed.runtime-enabled", "true");
        assertThrows(IllegalStateException.class, () -> bootstrap(jdbc, unsafe).run(null));
        verify(jdbc, never()).execute(anyString());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void bootstrapRefusesForeignBusinessRowsWithoutCallingFixtureServices() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("select current_database()", String.class)).thenReturn("restaurant_template_test");
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        TemplateRoutingService routing = mock(TemplateRoutingService.class);
        TemplateMenuService fixtureMenus = mock(TemplateMenuService.class);
        TemplateTestBootstrap bootstrap = new TemplateTestBootstrap(jdbc, bootstrapEnvironment(), mock(PasswordService.class), fixtureMenus, routing);
        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(fixtureMenus, routing);
    }

    private TemplateTestBootstrap bootstrap(JdbcTemplate jdbc, MockEnvironment environment) {
        return new TemplateTestBootstrap(jdbc, environment, mock(PasswordService.class), mock(TemplateMenuService.class), mock(TemplateRoutingService.class));
    }

    private MockEnvironment bootstrapEnvironment() {
        MockEnvironment environment = new MockEnvironment().withProperty("app.environment", "test")
            .withProperty("app.template.enabled", "true").withProperty("app.template.test-bootstrap.enabled", "true")
            .withProperty("UBER_EATS_ENABLED", "false").withProperty("UBER_EATS_WORKER_ENABLED", "false")
            .withProperty("TEMPLATE_TEST_OWNER_PASSWORD", "o".repeat(24)).withProperty("TEMPLATE_TEST_MANAGER_PASSWORD", "m".repeat(24));
        environment.setActiveProfiles("template-test");
        for (String flag : List.of("runtime-enabled", "safe-metadata-enabled", "default-users-enabled", "demo-data-enabled",
            "membership-supplement-enabled", "production-bootstrap-enabled")) environment.setProperty("app.seed." + flag, "false");
        return environment;
    }

    private MenuItem item(long id, long store) {
        MenuItem item = new MenuItem();
        item.id = id;
        item.store_id = store;
        item.station_id = 100L;
        item.category_id = store*1000L;
        item.base_price = new BigDecimal("10.00");
        item.is_active = true;
        item.is_sold_out = false;
        return item;
    }

    private Groups definition(String code, String kind, String mode, boolean required, Option... options) {
        Groups request = new Groups();
        Group group = new Group();
        group.group_instance_code = code;
        group.name = "SYNTHETIC " + code;
        group.kind = kind;
        group.selection_mode = mode;
        group.required = required;
        group.options = new ArrayList<>(List.of(options));
        request.groups.add(group);
        return request;
    }

    private Option option(String code, String name, String price, boolean selected) {
        Option option = new Option();
        option.option_code = code;
        option.name_zh = name;
        option.name_en = name;
        option.price_delta = new BigDecimal(price);
        option.is_default = selected;
        return option;
    }

    private CreateOrderItemRequest request(long item, CreateOrderItemOptionRequest... selected) {
        CreateOrderItemRequest request = new CreateOrderItemRequest();
        request.menu_item_id = item;
        request.quantity = 1;
        request.unit_price_snapshot = new BigDecimal("10.00");
        request.station_id_snapshot = 100L;
        request.options = new ArrayList<>(List.of(selected));
        return request;
    }

    private CreateOrderItemOptionRequest selected(Option option, int quantity) {
        CreateOrderItemOptionRequest request = new CreateOrderItemOptionRequest();
        request.option_id = option.id;
        request.option_price_snapshot = option.price_delta;
        request.option_code_snapshot = option.option_code;
        request.option_group_snapshot = option.option_group;
        request.quantity = quantity;
        return request;
    }

    private void assertCode(String code, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertTrue(exception.getMessage().startsWith(code), exception.getMessage());
    }
}
