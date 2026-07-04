package com.restaurant.system.printing.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import com.restaurant.system.printing.semantic.OptionSemanticResolver;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HotKitchenReceiptRendererTest {

    @Test
    void rendersWholeKitchenFacingLineForNoodleWithFriedEgg() {
        HotKitchenReceiptRenderer renderer = renderer();
        PrintRenderRequest request = baseRequest();
        request.is_update_ticket = true;

        String content = renderer.render(request);

        assertTrue(content.contains("UPDATED"));
        assertTrue(content.contains("HOT KITCHEN"));
        assertTrue(content.contains("桌号：T2"));
        assertTrue(content.contains("大二(S) | +煎蛋 +葱 x1"));
        assertTrue(content.contains("备注：less soup"));
    }

    @Test
    void rendersBlankWhenThereIsNoHotKitchenContent() {
        HotKitchenReceiptRenderer renderer = renderer();
        PrintRenderRequest request = baseRequest();
        request.order_item_options = List.of();

        assertEquals("", renderer.render(request));
    }

    @Test
    void comboFriedEggTicketDoesNotRenderColdComboSideTasks() {
        HotKitchenReceiptRenderer renderer = renderer();
        PrintRenderRequest request = baseRequest();
        OrderItemOption comboFriedEgg = request.order_item_options.get(0);
        comboFriedEgg.option_code_snapshot = "combo_fried_egg";
        comboFriedEgg.option_group_snapshot = "COMBO_EGG";

        KitchenTask edamameTask = new KitchenTask();
        edamameTask.id = 21L;
        edamameTask.order_id = request.order.id;
        edamameTask.order_item_id = request.order_items.get(0).id;
        edamameTask.station_code = "COLD";
        edamameTask.item_name_snapshot_zh = "毛豆";
        edamameTask.item_name_snapshot_en = "Edamame";
        edamameTask.special_instructions_snapshot = "毛豆";
        edamameTask.status = "pending";
        edamameTask.quantity = 1;
        edamameTask.priority = 100;

        KitchenTask potatoTask = new KitchenTask();
        potatoTask.id = 22L;
        potatoTask.order_id = request.order.id;
        potatoTask.order_item_id = request.order_items.get(0).id;
        potatoTask.station_code = "COLD";
        potatoTask.item_name_snapshot_zh = "土豆";
        potatoTask.item_name_snapshot_en = "Shredded Potato";
        potatoTask.special_instructions_snapshot = "走花生";
        potatoTask.status = "pending";
        potatoTask.quantity = 1;
        potatoTask.priority = 100;
        request.kitchen_tasks = List.of(request.kitchen_tasks.get(0), edamameTask, potatoTask);

        String content = renderer.render(request);

        assertTrue(content.contains("大二(S) | +煎蛋 +葱 x1"));
        assertFalse(content.contains("毛豆"));
        assertFalse(content.contains("Edamame"));
        assertFalse(content.contains("土豆"));
        assertFalse(content.contains("Shredded Potato"));
    }

    private HotKitchenReceiptRenderer renderer() {
        MenuItemRepository menuItemRepository = Mockito.mock(MenuItemRepository.class);
        MenuItemOptionRepository optionRepository = Mockito.mock(MenuItemOptionRepository.class);
        return new HotKitchenReceiptRenderer(
            new HotKitchenPrintEligibilityService(
                menuItemRepository,
                new OptionSemanticResolver(optionRepository)
            )
        );
    }

    private PrintRenderRequest baseRequest() {
        Order order = new Order();
        order.id = 1L;
        order.order_type = "dine_in";
        order.table_no = "T2";
        order.submitted_at = LocalDateTime.of(2026, 6, 16, 12, 23);

        OrderItem item = new OrderItem();
        item.id = 10L;
        item.order_id = order.id;
        item.item_name_snapshot_zh = "传统牛肉面";
        item.item_name_snapshot_en = "Traditional Beef Noodle";
        item.category_code_snapshot = "SOUP_NOODLE";
        item.quantity = 1;
        item.notes = "less soup";

        OrderItemOption friedEgg = new OrderItemOption();
        friedEgg.order_item_id = item.id;
        friedEgg.option_code_snapshot = "fried_egg";
        friedEgg.option_group_snapshot = "ADD_ON";
        friedEgg.option_name_snapshot_zh = "加煎蛋";
        friedEgg.option_name_snapshot_en = "Fried Egg";

        KitchenTask task = new KitchenTask();
        task.id = 20L;
        task.order_id = order.id;
        task.order_item_id = item.id;
        task.station_code = "NOODLE";
        task.item_name_snapshot_zh = "传统牛肉面";
        task.item_name_snapshot_en = "Traditional Beef Noodle";
        task.special_instructions_snapshot = "大二(S) | +煎蛋 +葱";
        task.status = "pending";
        task.quantity = 1;

        PrintRenderRequest request = new PrintRenderRequest();
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = List.of(friedEgg);
        request.kitchen_tasks = List.of(task);
        request.happened_at = LocalDateTime.now();
        return request;
    }
}
