package com.restaurant.system.printing.semantic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HotKitchenPrintEligibilityServiceTest {

    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private MenuItemOptionRepository menuItemOptionRepository;

    private HotKitchenPrintEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new HotKitchenPrintEligibilityService(
            menuItemRepository,
            new OptionSemanticResolver(menuItemOptionRepository)
        );
    }

    @Test
    void deepFriedStationRoutesEvenWhenItemNameChanges() {
        assertTrue(service.hasHotKitchenContent(request(item("Renamed Item", "OTHER", 20L), task("DEEPFRIED"), List.of())));
    }

    @Test
    void wokStationRoutesEvenWhenItemNameChanges() {
        assertTrue(service.hasHotKitchenContent(request(item("Renamed Item", "SOUP_NOODLE", 20L), task("WOK"), List.of())));
    }

    @Test
    void chowMeinSkuFallbackRoutesWithoutNameMatching() {
        MenuItem menuItem = new MenuItem();
        menuItem.id = 20L;
        menuItem.sku = "beef_chow_mein";
        when(menuItemRepository.findById(menuItem.id)).thenReturn(Optional.of(menuItem));

        assertTrue(service.hasHotKitchenContent(request(item("Renamed Item", "OTHER", menuItem.id), task("NOODLE"), List.of())));
    }

    @Test
    void normalNoodleWithoutFriedEggDoesNotRoute() {
        assertFalse(service.hasHotKitchenContent(request(item("Beef Noodle", "SOUP_NOODLE", 20L), task("NOODLE"), List.of())));
    }

    @Test
    void noodleWithExtraFriedEggRoutesByStableOptionCode() {
        OrderItemOption option = option("fried_egg", "ADD_ON", "Sunny Egg");

        assertTrue(service.hasHotKitchenContent(request(item("Beef Noodle", "SOUP_NOODLE", 20L), task("NOODLE"), List.of(option))));
    }

    @Test
    void comboWithFriedEggRoutesByStableGroupAndCode() {
        OrderItemOption option = option("combo_fried_egg", "COMBO_EGG", "Combo Sunny Egg");

        assertTrue(service.hasHotKitchenContent(request(item("Beef Noodle", "SOUP_NOODLE", 20L), task("NOODLE"), List.of(option))));
    }

    @Test
    void comboWithTeaEggDoesNotRouteAsHotKitchenEgg() {
        OrderItemOption option = option("combo_tea_egg", "COMBO_EGG", "Combo Tea Egg");

        assertFalse(service.hasHotKitchenContent(request(item("Beef Noodle", "SOUP_NOODLE", 20L), task("NOODLE"), List.of(option))));
    }

    @Test
    void comboFriedEggRoutesMainItemButDoesNotRouteColdComboEdamameSideTask() {
        OrderItem mainItem = item("Beef Noodle", "SOUP_NOODLE", 20L);
        mainItem.id = 100L;
        OrderItemOption comboFriedEgg = option("combo_fried_egg", "COMBO_EGG", "Combo Fried Egg");
        comboFriedEgg.order_item_id = mainItem.id;

        KitchenTask mainTask = task("NOODLE");
        mainTask.id = 201L;
        mainTask.order_item_id = mainItem.id;
        mainTask.item_name_snapshot_en = "Beef Noodle";
        mainTask.special_instructions_snapshot = "Medium Sour | +fried egg";

        KitchenTask edamameTask = task("COLD");
        edamameTask.id = 202L;
        edamameTask.order_item_id = mainItem.id;
        edamameTask.item_name_snapshot_en = "Edamame";
        edamameTask.special_instructions_snapshot = "Edamame";
        edamameTask.priority = 100;

        PrintRenderRequest request = new PrintRenderRequest();
        request.order_items = List.of(mainItem);
        request.order_item_options = List.of(comboFriedEgg);
        request.kitchen_tasks = List.of(mainTask, edamameTask);

        List<KitchenTask> hotTasks = service.resolveHotKitchenTasks(request);

        assertEquals(1, hotTasks.size());
        assertEquals(mainTask.id, hotTasks.get(0).id);
    }

    @Test
    void comboFriedEggDoesNotRouteColdComboShreddedPotatoSideTask() {
        OrderItem mainItem = item("Beef Noodle", "SOUP_NOODLE", 20L);
        mainItem.id = 100L;
        OrderItemOption comboFriedEgg = option("combo_fried_egg", "COMBO_EGG", "Combo Fried Egg");
        comboFriedEgg.order_item_id = mainItem.id;

        KitchenTask potatoTask = task("COLD");
        potatoTask.id = 203L;
        potatoTask.order_item_id = mainItem.id;
        potatoTask.item_name_snapshot_en = "Shredded Potato";
        potatoTask.special_instructions_snapshot = "No Peanut";
        potatoTask.priority = 100;

        PrintRenderRequest request = new PrintRenderRequest();
        request.order_items = List.of(mainItem);
        request.order_item_options = List.of(comboFriedEgg);
        request.kitchen_tasks = List.of(potatoTask);

        assertFalse(service.hasHotKitchenContent(request));
    }

    @Test
    void syntheticComboSideTaskCanRouteOnlyWhenItsOwnStationIsHotKitchen() {
        OrderItem mainItem = item("Beef Noodle", "SOUP_NOODLE", 20L);
        mainItem.id = 100L;

        KitchenTask friedSideTask = task("DEEPFRIED");
        friedSideTask.id = 204L;
        friedSideTask.order_item_id = mainItem.id;
        friedSideTask.item_name_snapshot_en = "Future Fried Combo Side";
        friedSideTask.priority = 100;

        PrintRenderRequest request = new PrintRenderRequest();
        request.order_items = List.of(mainItem);
        request.order_item_options = List.of();
        request.kitchen_tasks = List.of(friedSideTask);

        assertTrue(service.hasHotKitchenContent(request));
    }

    @Test
    void actualComboSideOrderItemDoesNotRouteFromEggOptionsUnlessItIsItselfHot() {
        OrderItem sideItem = item("Combo Edamame", "SIDE", 20L);
        sideItem.combo_role = "combo_side";
        OrderItemOption comboFriedEgg = option("combo_fried_egg", "COMBO_EGG", "Combo Fried Egg");

        assertFalse(service.hasHotKitchenContent(request(sideItem, task("COLD"), List.of(comboFriedEgg))));
    }

    private PrintRenderRequest request(OrderItem item, KitchenTask task, List<OrderItemOption> options) {
        item.id = 100L;
        task.order_item_id = item.id;
        for (OrderItemOption option : options) {
            option.order_item_id = item.id;
        }
        PrintRenderRequest request = new PrintRenderRequest();
        request.order_items = List.of(item);
        request.kitchen_tasks = List.of(task);
        request.order_item_options = options;
        return request;
    }

    private OrderItem item(String name, String categoryCode, Long menuItemId) {
        OrderItem item = new OrderItem();
        item.menu_item_id = menuItemId;
        item.item_name_snapshot_en = name;
        item.category_code_snapshot = categoryCode;
        item.quantity = 1;
        return item;
    }

    private KitchenTask task(String stationCode) {
        KitchenTask task = new KitchenTask();
        task.id = 200L;
        task.station_code = stationCode;
        task.status = "pending";
        task.quantity = 1;
        return task;
    }

    private OrderItemOption option(String code, String group, String nameEn) {
        OrderItemOption option = new OrderItemOption();
        option.option_code_snapshot = code;
        option.option_group_snapshot = group;
        option.option_name_snapshot_en = nameEn;
        option.quantity = 1;
        return option;
    }
}
