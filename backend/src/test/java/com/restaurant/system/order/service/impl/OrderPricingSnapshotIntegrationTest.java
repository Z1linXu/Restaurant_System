package com.restaurant.system.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.pricing.TaxCalculator;
import com.restaurant.system.common.realtime.RealtimeEventPublisher;
import com.restaurant.system.menu.addon.StoreAddonOptionResolver;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.order.dto.CreateOrderUpdateRequest;
import com.restaurant.system.order.dto.OrderResponse;
import com.restaurant.system.order.dto.UpdateDraftOrderItemQuantityRequest;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemOptionRepository;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.renderer.FrontdeskReceiptRenderer;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import com.restaurant.system.printing.rules.PrintingDisplayRuleService;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
    "spring.datasource.url=jdbc:h2:mem:order_pricing;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = OrderPricingSnapshotIntegrationTest.JpaConfiguration.class)
@Import({OrderServiceImpl.class, StoreAddonOptionResolver.class, com.restaurant.system.menu.remove.StoreRemoveOptionResolver.class, com.restaurant.system.menu.remove.RemoveSelectionValidator.class, com.restaurant.system.station.service.DiningTableDisplayService.class})
class OrderPricingSnapshotIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaConfiguration {}

    @Autowired private OrderServiceImpl service;
    @Autowired private OrganizationRepository organizations;
    @Autowired private StoreRepository stores;
    @Autowired private MenuCategoryRepository categories;
    @Autowired private StationRepository stations;
    @Autowired private MenuItemRepository menuItems;
    @Autowired private MenuItemOptionRepository menuOptions;
    @Autowired private OrderRepository orders;
    @Autowired private OrderItemRepository orderItems;
    @Autowired private OrderItemOptionRepository orderOptions;
    @Autowired private EntityManager entityManager;
    @Autowired private com.restaurant.system.menu.combo.StoreComboGroupRepository comboGroups;
    @Autowired private com.restaurant.system.menu.combo.StoreComboComponentRepository comboComponents;
    @MockBean private RealtimeEventPublisher realtime;
    @MockBean private PrintDispatcherService printing;
    @MockBean private StoreComboConfigurationService comboConfiguration;
    @MockBean private PrintingDisplayRuleService printingRules;
    private Store store;
    private MenuItem item;

    @BeforeEach
    void fixture() {
        LocalDateTime now = LocalDateTime.now();
        String key = UUID.randomUUID().toString();
        Organization organization = new Organization();
        organization.code = key; organization.name = "Pricing test"; organization.status = "active";
        organization.created_at = now; organization.updated_at = now;
        organizations.saveAndFlush(organization);
        store = new Store();
        store.organization_id = organization.id; store.code = key; store.name = "Pricing test";
        store.status = "active"; store.printing_enabled = false; store.printing_mode = "DISABLED";
        store.created_at = now; store.updated_at = now;
        stores.saveAndFlush(store);
        Station station = new Station();
        station.store_id = store.id; station.code = "NOODLE"; station.name_zh = "拉面";
        station.name_en = "Noodle"; station.is_active = true; station.created_at = now; station.updated_at = now;
        stations.saveAndFlush(station);
        MenuCategory category = new MenuCategory();
        category.store_id = store.id; category.code = "SOUP_NOODLE"; category.name_zh = "汤面";
        category.name_en = "Soup noodles"; category.is_active = true; category.created_at = now; category.updated_at = now;
        categories.saveAndFlush(category);
        item = new MenuItem();
        item.store_id = store.id; item.category_id = category.id; item.station_id = station.id;
        item.sku = "traditional_beef_noodle"; item.name_zh = "牛肉面"; item.name_en = "Beef Noodle";
        item.base_price = new BigDecimal("16.99"); item.is_active = true; item.is_sold_out = false;
        item.created_at = now; item.updated_at = now;
        menuItems.saveAndFlush(item);
        when(printingRules.activeContext(anyLong())).thenReturn(PrintingDisplayRuleContext.defaultContext());
    }

    @ParameterizedTest
    @CsvSource({
        "0.00,1,false,0,16.99,2.54,19.53",
        "2.00,1,false,0,18.99,2.84,21.83",
        "2.00,2,false,0,37.98,5.69,43.67",
        "-2.00,2,false,0,29.98,4.49,34.47",
        "0.00,1,true,0,21.99,3.29,25.28",
        "2.00,1,true,0,23.99,3.59,27.58",
        "2.00,2,true,0,47.98,7.19,55.17",
        "2.00,2,true,1,51.96,7.78,59.74",
        "2.00,2,true,2,55.94,8.38,64.32"
    })
    void dtoToDatabaseToReceiptKeepsPerUnitModifiersAndTax(
        String size, int quantity, boolean combo, int extraEggs, String subtotal, String tax, String total
    ) throws Exception {
        CreateOrderItemRequest line = line(size, quantity, combo, extraEggs);
        CreateOrderRequest request = request(List.of(line));
        // Exercise the public DTO shape, rather than a hand-built inconsistent renderer fixture.
        ObjectMapper mapper = new ObjectMapper();
        request = mapper.readValue(mapper.writeValueAsString(request), CreateOrderRequest.class);
        OrderResponse created = service.createOrder(request);
        OrderResponse submitted = service.submitOrder(created.id);
        entityManager.flush();
        entityManager.clear();

        assertStoredAndRendered(submitted.id, subtotal, tax, total);
        assertThat(orderItems.findAllByOrderId(submitted.id).get(0).unit_price).isEqualByComparingTo("16.99");
        assertThat(orderOptions.findAllByOrderItemIds(List.of(submitted.items.get(0).id)))
            .filteredOn(option -> "SIZE".equals(option.option_group_snapshot))
            .allSatisfy(option -> assertThat(option.quantity).isEqualTo(1));
    }

    @Test
    void differentSizesAndAddonsStaySeparateAndSumCorrectly() {
        OrderResponse draft = service.createOrder(request(List.of(
            line("0.00", 1, false, 0), line("2.00", 1, false, 0), line("2.00", 1, false, 1)
        )));
        assertThat(draft.items).hasSize(3);
        assertThat(draft.items).extracting(row -> row.line_amount).containsExactly(
            new BigDecimal("16.99"), new BigDecimal("18.99"), new BigDecimal("20.98")
        );
        assertThat(draft.subtotal_amount).isEqualByComparingTo("56.96");
    }

    @Test
    void quantityChangeAndLegacyDraftSubmitUseFrozenPricesOnly() {
        OrderResponse draft = service.createOrder(request(List.of(line("2.00", 1, false, 0))));
        UpdateDraftOrderItemQuantityRequest quantity = new UpdateDraftOrderItemQuantityRequest();
        quantity.quantity = 2;
        assertThat(service.updateDraftOrderItemQuantity(draft.id, draft.items.get(0).id, quantity).subtotal_amount)
            .isEqualByComparingTo("37.98");
        OrderItem legacyLine = orderItems.findById(draft.items.get(0).id).orElseThrow();
        legacyLine.line_amount = new BigDecimal("35.98");
        item.base_price = new BigDecimal("99.00");
        menuItems.save(item);
        menuOptions.findAllByMenuItemIdOrdered(item.id).forEach(option -> option.price_delta = new BigDecimal("12.00"));
        entityManager.flush();
        entityManager.clear();

        service.submitOrder(draft.id);
        entityManager.flush();
        entityManager.clear();
        assertStoredAndRendered(draft.id, "37.98", "5.69", "43.67");
    }

    @Test
    void updatePricesOnlyNewFrozenLinesAndKeepsHistoricalSubmittedAmounts() {
        OrderResponse original = service.createOrder(request(List.of(line("2.00", 2, false, 0))));
        service.submitOrder(original.id);
        OrderItem historical = orderItems.findById(original.items.get(0).id).orElseThrow();
        historical.line_amount = new BigDecimal("35.98");
        Order oldOrder = orders.findById(original.id).orElseThrow();
        oldOrder.subtotal_amount = historical.line_amount;
        oldOrder.total_amount = TaxCalculator.calculateTotal(historical.line_amount);
        entityManager.flush();
        entityManager.clear();
        CreateOrderUpdateRequest update = new CreateOrderUpdateRequest();
        update.idempotency_key = "pricing-update";
        update.items = List.of(line("2.00", 2, true, 0));
        var result = service.createOrderUpdate(original.id, update, null);
        var duplicate = service.createOrderUpdate(original.id, update, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(result.already_processed).isFalse();
        assertThat(duplicate.already_processed).isTrue();
        List<OrderItem> persisted = orderItems.findAllByOrderId(original.id);
        assertThat(persisted).hasSize(2);
        assertThat(persisted.get(0).line_amount).isEqualByComparingTo("35.98");
        assertThat(persisted.get(1).line_amount).isEqualByComparingTo("47.98");
        // Child entity loading can leave a lazy Order proxy in the persistence context.
        entityManager.clear();
        assertStoredAndRendered(original.id, "83.96", "12.57", "96.53");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"combo_cucumber_salad", "combo_private_daily_side"})
    void configuredSideBindingPersistsRemoveParentWithoutClientParentSnapshot(String componentCode) {
        Station cold = new Station();
        cold.store_id = store.id; cold.code = "COLD"; cold.name_zh = "凉菜"; cold.name_en = "Cold";
        cold.is_active = true; cold.created_at = LocalDateTime.now(); cold.updated_at = cold.created_at;
        stations.saveAndFlush(cold);
        MenuItem side = new MenuItem();
        side.store_id = store.id; side.category_id = item.category_id; side.station_id = cold.id;
        side.sku = "private_rebound_side"; side.name_zh = "改绑小菜"; side.name_en = "Bound side";
        side.base_price = BigDecimal.ZERO; side.is_active = true; side.is_sold_out = false;
        side.created_at = LocalDateTime.now(); side.updated_at = side.created_at;
        menuItems.saveAndFlush(side);
        var group = new com.restaurant.system.menu.combo.StoreComboGroup();
        group.store_id = store.id; group.group_code = "COMBO_SIDE"; group.name_zh = "小菜"; group.name_en = "Side";
        group.selection_rule = "EXACTLY_ONE"; group.required = true; group.enabled = true; group.display_order = 0;
        group.created_at = LocalDateTime.now(); group.updated_at = group.created_at;
        comboGroups.saveAndFlush(group);
        var component = new com.restaurant.system.menu.combo.StoreComboComponent();
        component.store_id = store.id; component.group_id = group.id; component.component_group = "COMBO_SIDE";
        component.component_code = componentCode; component.name_zh = "改绑小菜"; component.name_en = "Bound side";
        component.enabled = true; component.display_order = 0; component.business_behavior = "LINKED_MENU_ITEM";
        component.linked_menu_item_id = side.id; component.created_at = LocalDateTime.now(); component.updated_at = component.created_at;
        comboComponents.saveAndFlush(component);
        CreateOrderItemRequest line = line("0.00", 1, true, 0);
        CreateOrderItemOptionRequest parent = line.options.stream().filter(o -> "COMBO_SIDE".equals(o.option_group_snapshot)).findFirst().orElseThrow();
        menuOptions.findById(parent.option_id).orElseThrow().option_code = componentCode;
        parent.option_code_snapshot = componentCode;
        var removal = option("remove", "REMOVE", "remove_peanut", "0.00", 1);
        menuOptions.findById(removal.option_id).orElseThrow().menu_item_id = side.id;
        entityManager.flush();
        assertThat(removal.parent_option_id_snapshot).isNull();
        line.options.add(removal);
        OrderResponse draft = service.createOrder(request(List.of(line)));
        service.submitOrder(draft.id);
        entityManager.flush(); entityManager.clear();
        assertThat(orderOptions.findAllByOrderItemIds(List.of(draft.items.get(0).id)))
            .filteredOn(o -> "remove_peanut".equals(o.option_code_snapshot)).singleElement().satisfies(o -> {
                assertThat(o.option_group_snapshot).isEqualTo("COMBO_SIDE_REMOVE");
                assertThat(o.parent_option_id_snapshot).isEqualTo(parent.option_id);
                assertThat(o.price_delta).isEqualByComparingTo(BigDecimal.ZERO);
            });
    }

    private CreateOrderRequest request(List<CreateOrderItemRequest> lines) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.store_id = store.id; request.order_type = "pickup";
        request.pickup_no = UUID.randomUUID().toString(); request.items = lines;
        return request;
    }

    private CreateOrderItemRequest line(String size, int quantity, boolean combo, int extraEggs) {
        CreateOrderItemRequest line = new CreateOrderItemRequest();
        line.menu_item_id = item.id; line.item_name_snapshot_zh = item.name_zh; line.item_name_snapshot_en = item.name_en;
        line.category_code_snapshot = "SOUP_NOODLE"; line.station_id_snapshot = item.station_id;
        line.unit_price_snapshot = new BigDecimal("16.99"); line.item_sku_snapshot = item.sku;
        line.quantity = quantity; line.combo_role = "standalone";
        line.options = new ArrayList<>();
        line.options.add(option("size", "SIZE", "size_" + (size.startsWith("-") ? "small" : size.startsWith("2") ? "large" : "regular"), size, 1));
        if (combo) {
            line.options.add(option("addon", "COMBO", "combo", "5.00", 1));
            line.options.add(option("addon", "COMBO_EGG", "combo_tea_egg", "0.00", 1));
            line.options.add(option("addon", "COMBO_SIDE", "combo_daily_side", "0.00", 1));
        }
        if (extraEggs > 0) line.options.add(option("addon", "ADD_ON", "tea_egg", "1.99", extraEggs));
        return line;
    }

    private CreateOrderItemOptionRequest option(String type, String group, String code, String delta, int quantity) {
        MenuItemOption option = new MenuItemOption();
        option.menu_item_id = item.id; option.option_type = type; option.option_group = group; option.option_code = code;
        option.name_zh = code; option.name_en = code; option.price_delta = new BigDecimal(delta); option.is_active = true;
        option.created_at = LocalDateTime.now(); option.updated_at = option.created_at;
        menuOptions.saveAndFlush(option);
        CreateOrderItemOptionRequest request = new CreateOrderItemOptionRequest();
        request.option_id = option.id; request.quantity = quantity;
        request.option_type_snapshot = type; request.option_group_snapshot = group; request.option_code_snapshot = code;
        request.option_name_snapshot_zh = code; request.option_name_snapshot_en = code;
        request.option_price_snapshot = option.price_delta;
        return request;
    }

    private void assertStoredAndRendered(Long orderId, String subtotal, String tax, String total) {
        Order stored = orders.findById(orderId).orElseThrow();
        assertThat(stored.subtotal_amount).isEqualByComparingTo(subtotal);
        assertThat(stored.total_amount.subtract(stored.subtotal_amount)).isEqualByComparingTo(tax);
        assertThat(stored.total_amount).isEqualByComparingTo(total);
        String rendered = assertReceiptConservation(orderId);
        assertThat(rendered).contains("Subtotal: $" + subtotal, "Tax (14.975%): $" + tax, "Total: $" + total);
    }

    private String assertReceiptConservation(Long orderId) {
        PrintRenderRequest request = new PrintRenderRequest();
        request.order = orders.findById(orderId).orElseThrow();
        request.order_items = orderItems.findAllByOrderId(orderId);
        request.order_item_options = orderOptions.findAllByOrderItemIds(request.order_items.stream().map(row -> row.id).toList());
        String rendered = new FrontdeskReceiptRenderer().render(request).replaceAll("\\[\\[/?[^]]+]]", "");
        BigDecimal displayed = rendered.lines().filter(row -> row.startsWith("$"))
            .map(row -> new BigDecimal(row.substring(1))).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(displayed).isEqualByComparingTo(request.order.subtotal_amount);
        return rendered;
    }
}
