package com.restaurant.system.printing.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.dto.ExternalKitchenSnapshot;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import com.restaurant.system.printing.semantic.OptionSemanticResolver;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExternalOrderNoteRenderingTest {
    private static final String ORDER_NOTE = "Please make everything less salty";

    @ParameterizedTest
    @ValueSource(strings = {PrintModuleCode.GRAB, PrintModuleCode.HOT_KITCHEN})
    void tenItemsKeepDistinctItemNotesAndRenderOrderNoteOnceAtTicketEnd(String module) {
        PrintRenderRequest request = request();
        String content = render(module, request);

        assertEquals(1, count(content, ORDER_NOTE));
        assertEquals(1, count(content, "订单备注："));
        assertEquals(1, count(content, "No cilantro"));
        assertEquals(1, count(content, "Extra soup"));
        assertTrue(content.indexOf("菜品1") < content.indexOf("No cilantro"));
        assertTrue(content.indexOf("No cilantro") < content.indexOf("菜品2"));
        assertTrue(content.indexOf("菜品2") < content.indexOf("Extra soup"));
        assertTrue(content.indexOf("Extra soup") < content.indexOf("菜品3"));
        assertTrue(content.indexOf("菜品10") < content.indexOf("订单备注："));
        assertTrue(content.indexOf(ORDER_NOTE) < content.indexOf("外卖"));
    }

    @ParameterizedTest
    @ValueSource(strings = {PrintModuleCode.GRAB, PrintModuleCode.HOT_KITCHEN})
    void noOrderNoteLeavesCustomerItemNotesUntouched(String module) {
        PrintRenderRequest request = request();
        request.order.external_order_note_snapshot = null;
        String content = render(module, request);
        assertFalse(content.contains("订单备注："));
        assertEquals(1, count(content, "No cilantro"));
        assertEquals(1, count(content, "Extra soup"));
    }

    @ParameterizedTest
    @ValueSource(strings = {PrintModuleCode.GRAB, PrintModuleCode.HOT_KITCHEN})
    void localItemNotesAreUnchangedAndExternalNoteRequiresUberSource(String module) {
        PrintRenderRequest request = request();
        request.order.financial_mode = "IN_STORE";
        request.order.order_type = "dine_in";
        request.order.external_source = null;
        String content = render(module, request);
        assertFalse(content.contains("订单备注："));
        assertFalse(content.contains(ORDER_NOTE));
        assertEquals(1, count(content, "No cilantro"));
        assertEquals(1, count(content, "Extra soup"));
        request.order.external_source = "OTHER_PLATFORM";
        assertFalse(render(module, request).contains(ORDER_NOTE));
    }

    @ParameterizedTest
    @ValueSource(strings = {PrintModuleCode.GRAB, PrintModuleCode.HOT_KITCHEN})
    void mixedRawAndMappedItemsKeepOneWholeOrderNoteOnEachApplicableTicket(String module) {
        PrintRenderRequest request = request();
        OrderItem raw = request.order_items.get(9);
        raw.externalKitchenSnapshot = new ExternalKitchenSnapshot("raw-root", true, "Raw dish", 1,
                "Raw item customer note", List.of());
        request.kitchen_tasks.get(9).station_code = "RAW_UBER_FALLBACK";
        String content = render(module, request);
        assertEquals(1, count(content, ORDER_NOTE));
        assertEquals(1, count(content, "No cilantro"));
        assertEquals(PrintModuleCode.GRAB.equals(module) ? 1 : 0, count(content, "Raw item customer note"));
        assertTrue(content.indexOf(ORDER_NOTE) < content.indexOf("外卖"));
    }

    @ParameterizedTest
    @ValueSource(strings = {PrintModuleCode.GRAB, PrintModuleCode.HOT_KITCHEN})
    void legacyUberOrderManagerAlsoPrintsTheFrozenOrderNote(String module) {
        PrintRenderRequest request = request();
        request.order.financial_mode = "IN_STORE";
        assertEquals(1, count(render(module, request), ORDER_NOTE));
    }

    @ParameterizedTest
    @ValueSource(strings = {PrintModuleCode.GRAB, PrintModuleCode.HOT_KITCHEN})
    void orderNoteCannotInjectPrinterControlsOrMarkup(String module) {
        PrintRenderRequest request = request();
        request.order.external_order_note_snapshot = "Keep note \u001B[[LARGE]]not markup[[/LARGE]]\u0007\u202E";
        String content = render(module, request);
        assertFalse(content.contains("\u001B"));
        assertFalse(content.contains("\u0007"));
        assertFalse(content.contains("\u202E"));
        assertTrue(content.contains("Keep note  ［［LARGE］］not markup［［/LARGE］］"));
    }

    private String render(String module, PrintRenderRequest request) {
        ReceiptRenderer renderer = PrintModuleCode.GRAB.equals(module) ? new GrabReceiptRenderer()
                : new HotKitchenReceiptRenderer(new HotKitchenPrintEligibilityService(
                        mock(MenuItemRepository.class), new OptionSemanticResolver(mock(MenuItemOptionRepository.class))));
        return renderer.render(request);
    }

    private PrintRenderRequest request() {
        Order order = new Order();
        order.id = 100L;
        order.store_id = 1L;
        order.order_type = "delivery";
        order.financial_mode = "EXTERNAL_PLATFORM";
        order.external_source = "UBER_EATS";
        order.external_display_id = "TEST1";
        order.external_order_note_snapshot = ORDER_NOTE;
        order.submitted_at = LocalDateTime.of(2026, 10, 3, 14, 0);
        PrintRenderRequest request = new PrintRenderRequest();
        request.order = order;
        request.order_items = new ArrayList<>();
        request.kitchen_tasks = new ArrayList<>();
        request.order_item_options = List.of();
        for (long id = 1; id <= 10; id++) {
            OrderItem item = new OrderItem();
            item.id = id;
            item.order_id = order.id;
            item.menu_item_id = id;
            item.item_name_snapshot_zh = "菜品" + id;
            item.category_code_snapshot = "FRIED_NOODLE";
            item.quantity = 1;
            item.notes = id == 1 ? "No cilantro" : id == 2 ? "Extra soup" : null;
            request.order_items.add(item);
            KitchenTask task = new KitchenTask();
            task.id = id;
            task.order_id = order.id;
            task.order_item_id = id;
            task.item_name_snapshot_zh = item.item_name_snapshot_zh;
            task.station_code = "WOK";
            task.quantity = 1;
            task.status = "pending";
            task.created_at = order.submitted_at.plusSeconds(id);
            request.kitchen_tasks.add(task);
        }
        return request;
    }

    private int count(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
}
