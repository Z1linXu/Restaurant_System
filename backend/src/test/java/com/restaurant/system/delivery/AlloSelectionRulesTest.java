package com.restaurant.system.delivery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.template.TemplateItemOptionGroup;
import com.restaurant.system.menu.template.TemplateItemOptionGroupRepository;
import com.restaurant.system.menu.template.TemplateMenuService;
import com.restaurant.system.menu.template.TemplateStoreConfigurationRepository;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Synthetic public-service tests; no customer menu file or database is required. */
class AlloSelectionRulesTest {
    private static final long STORE = 17L;
    private static final long DUO = 101L;
    private static final long NOODLE_MAIN = 102L;
    private static final long NOODLE_REFILL = 103L;
    private static final long RICE_REFILL = 104L;
    private static final long CATEGORY = 201L;

    private final MenuItemRepository items = mock(MenuItemRepository.class);
    private final MenuItemOptionRepository options = mock(MenuItemOptionRepository.class);
    private final TemplateItemOptionGroupRepository groups = mock(TemplateItemOptionGroupRepository.class);
    private final TemplateStoreConfigurationRepository configs = mock(TemplateStoreConfigurationRepository.class);
    private final MenuRevisionService revisions = mock(MenuRevisionService.class);
    private final MenuCategoryRepository categories = mock(MenuCategoryRepository.class);
    private final Map<Long, MenuItem> catalog = new HashMap<>();
    private final List<MenuItemOption> proteins = new ArrayList<>();
    private TemplateItemOptionGroup proteinGroup;
    private TemplateMenuService service;

    @BeforeEach
    void setUp() {
        catalog.put(DUO, item(DUO, "19.99", null));
        catalog.put(NOODLE_MAIN, item(NOODLE_MAIN, "16.99", null));
        catalog.put(NOODLE_REFILL, item(NOODLE_REFILL, "0.00", "NOODLE"));
        catalog.put(RICE_REFILL, item(RICE_REFILL, "0.00", "RICE"));
        when(items.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(catalog.get(call.getArgument(0))));
        MenuCategory category = new MenuCategory();
        category.id = CATEGORY;
        category.store_id = STORE;
        category.code = "RICE";
        when(categories.findById(CATEGORY)).thenReturn(Optional.of(category));

        proteinGroup = new TemplateItemOptionGroup();
        proteinGroup.store_id = STORE;
        proteinGroup.menu_item_id = DUO;
        proteinGroup.group_code = "synthetic_duo";
        proteinGroup.name = "Synthetic choose two";
        proteinGroup.kind = "CUSTOM";
        proteinGroup.selection_mode = "MULTIPLE";
        proteinGroup.required = true;
        proteinGroup.is_active = true;
        proteinGroup.min_select = 2;
        proteinGroup.max_select = 2;
        for (long id = 501L; id <= 504L; id++) {
            MenuItemOption option = new MenuItemOption();
            option.id = id;
            option.menu_item_id = DUO;
            option.template_group_code = proteinGroup.group_code;
            option.option_type = "addon";
            option.option_group = "CUSTOM";
            option.option_code = "synthetic_protein_" + id;
            option.name_zh = "Synthetic protein " + id;
            option.name_en = option.name_zh;
            option.price_delta = new BigDecimal("0.00");
            option.is_active = true;
            proteins.add(option);
        }
        when(options.findAllByMenuItemIdOrdered(DUO)).thenReturn(proteins);
        when(groups.forItem(DUO)).thenReturn(List.of(proteinGroup));
        service = new TemplateMenuService(items, options, groups, configs, revisions, new ObjectMapper(),
            new MockEnvironment().withProperty("app.template.enabled", "true"), categories);
    }

    @Test
    void takeoutCannotReceiveEvenMatchingMainRefill() {
        assertCode("REFILL_DINE_IN_REQUIRED", () -> service.validateRefillEligibility(order("takeout"),
            List.of(line(NOODLE_MAIN, "NOODLE", "16.99"), line(NOODLE_REFILL, "NOODLE", "0.00"))));
    }

    @Test
    void refillWithoutPaidMainIsRejected() {
        assertCode("REFILL_MATCHING_MAIN_REQUIRED", () -> service.validateRefillEligibility(order("dine_in"),
            List.of(line(NOODLE_REFILL, "NOODLE", "0.00"))));
    }

    @Test
    void paidRiceCannotQualifyNoodleRefill() {
        assertCode("REFILL_MATCHING_MAIN_REQUIRED", () -> service.validateRefillEligibility(order("dine_in"),
            List.of(line(DUO, "RICE", "19.99"), line(NOODLE_REFILL, "NOODLE", "0.00"))));
    }

    @Test
    void cancelledMatchingMainCannotQualifyRefill() {
        OrderItem main = line(NOODLE_MAIN, "NOODLE", "16.99");
        main.status = "cancelled";
        assertCode("REFILL_MATCHING_MAIN_REQUIRED", () -> service.validateRefillEligibility(order("dine_in"),
            List.of(main, line(NOODLE_REFILL, "NOODLE", "0.00"))));
    }

    @Test
    void sameCategoryPaidDineInMainQualifiesBothRefillTypes() {
        assertDoesNotThrow(() -> service.validateRefillEligibility(order("dine_in"), List.of(
            line(NOODLE_MAIN, "NOODLE", "16.99"), line(NOODLE_REFILL, "NOODLE", "0.00"))));
        assertDoesNotThrow(() -> service.validateRefillEligibility(order("dine_in"), List.of(
            line(DUO, "RICE", "19.99"), line(RICE_REFILL, "RICE", "0.00"))));
    }

    @Test
    void anyNumberOfZeroPriceRefillsCannotProveMainQualification() {
        OrderItem first = line(NOODLE_REFILL, "NOODLE", "0.00");
        OrderItem second = line(NOODLE_REFILL, "NOODLE", "0.00");
        second.quantity = 3;
        assertCode("REFILL_MATCHING_MAIN_REQUIRED", () -> service.validateRefillEligibility(order("dine_in"),
            List.of(first, second)));
    }

    @Test
    void zeroQuantityMainDoesNotQualifyAndCancelledRefillDoesNotBlock() {
        OrderItem main = line(NOODLE_MAIN, "NOODLE", "16.99");
        main.quantity = 0;
        assertCode("REFILL_MATCHING_MAIN_REQUIRED", () -> service.validateRefillEligibility(order("dine_in"),
            List.of(main, line(NOODLE_REFILL, "NOODLE", "0.00"))));
        OrderItem cancelled = line(NOODLE_REFILL, "NOODLE", "0.00");
        cancelled.status = "cancelled";
        assertDoesNotThrow(() -> service.validateRefillEligibility(order("takeout"), List.of(cancelled)));
    }

    @Test
    void initialSnapshotValidationUsesRefillGuardBeforeSelectionValidation() {
        assertCode("REFILL_DINE_IN_REQUIRED", () -> service.validateSnapshots(order("takeout"),
            List.of(line(NOODLE_REFILL, "NOODLE", "0.00")), List.of()));
        verifyNoInteractions(options, groups, categories);
    }

    @Test
    void explicitlyConfiguredDuoAcceptsTwoDifferentQuantityOneChoices() {
        assertDoesNotThrow(() -> service.validateSelection(STORE, selection(selected(0, 1), selected(1, 1))));
    }

    @Test
    void explicitlyConfiguredDuoRejectsOneOrThreeChoices() {
        assertCode("TEMPLATE_GROUP_CARDINALITY_INVALID", () -> service.validateSelection(STORE, selection(selected(0, 1))));
        assertCode("TEMPLATE_GROUP_CARDINALITY_INVALID", () -> service.validateSelection(STORE,
            selection(selected(0, 1), selected(1, 1), selected(2, 1))));
    }

    @Test
    void repeatedOptionIdentityIsNotTwoDifferentProteins() {
        assertCode("TEMPLATE_OPTION_INVALID", () -> service.validateSelection(STORE,
            selection(selected(0, 1), selected(0, 1))));
    }

    @Test
    void twoDifferentChoicesCannotSmuggleQuantityTwo() {
        assertCode("TEMPLATE_GROUP_CHOICE_QUANTITY_INVALID", () -> service.validateSelection(STORE,
            selection(selected(0, 2), selected(1, 1))));
    }

    @Test
    void legacyMultipleGroupWithoutCardinalityRetainsItsQuantityAndCountBehavior() {
        proteinGroup.min_select = null;
        proteinGroup.max_select = null;
        proteinGroup.required = false;
        assertDoesNotThrow(() -> service.validateSelection(STORE,
            selection(selected(0, 3), selected(1, 1), selected(2, 1))));
        assertDoesNotThrow(() -> service.validateSelection(STORE, selection()));
    }

    @Test
    void featureOffPreservesLegacyRefillAndSelectionPath() {
        TemplateMenuService disabled = new TemplateMenuService(items, options, groups, configs, revisions,
            new ObjectMapper(), new MockEnvironment(), categories);
        assertDoesNotThrow(() -> disabled.validateRefillEligibility(order("takeout"),
            List.of(line(NOODLE_REFILL, "NOODLE", "0.00"))));
        assertDoesNotThrow(() -> disabled.validateSelection(STORE, selection(selected(0, 2))));
        verifyNoInteractions(items, options, groups, categories);
    }

    private MenuItem item(long id, String price, String refillCategory) {
        MenuItem row = new MenuItem();
        row.id = id;
        row.store_id = STORE;
        row.category_id = CATEGORY;
        row.station_id = 301L;
        row.sku = "SYNTHETIC_" + id;
        row.base_price = new BigDecimal(price);
        row.refill_category_code = refillCategory;
        row.template_option_groups_enabled = true;
        row.is_active = true;
        row.is_sold_out = false;
        return row;
    }

    private Order order(String type) {
        Order order = new Order();
        order.id = 401L;
        order.store_id = STORE;
        order.order_type = type;
        return order;
    }

    private OrderItem line(long item, String category, String price) {
        OrderItem row = new OrderItem();
        row.order_id = 401L;
        row.menu_item_id = item;
        row.category_code_snapshot = category;
        row.unit_price = new BigDecimal(price);
        row.quantity = 1;
        return row;
    }

    private CreateOrderItemRequest selection(CreateOrderItemOptionRequest... selected) {
        CreateOrderItemRequest request = new CreateOrderItemRequest();
        request.menu_item_id = DUO;
        request.quantity = 1;
        request.unit_price_snapshot = new BigDecimal("19.99");
        request.category_code_snapshot = "RICE";
        request.station_id_snapshot = 301L;
        request.item_sku_snapshot = "SYNTHETIC_" + DUO;
        request.options = new ArrayList<>(List.of(selected));
        return request;
    }

    private CreateOrderItemOptionRequest selected(int index, int quantity) {
        MenuItemOption option = proteins.get(index);
        CreateOrderItemOptionRequest selected = new CreateOrderItemOptionRequest();
        selected.option_id = option.id;
        selected.option_code_snapshot = option.option_code;
        selected.option_group_snapshot = option.option_group;
        selected.option_price_snapshot = option.price_delta;
        selected.quantity = quantity;
        return selected;
    }

    private void assertCode(String expected, Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertTrue(error.getMessage().startsWith(expected), error.getMessage());
    }
}
