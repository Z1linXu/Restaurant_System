package com.restaurant.system.printing.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrabReceiptRendererTest {

    private final GrabReceiptRenderer renderer = new GrabReceiptRenderer();

    @Test
    void groupsSameSideDishWithoutDemands() {
        String output = renderSides(
            side(1L, "牛展", "牛展"),
            side(2L, "牛展", "牛展")
        );

        assertTrue(output.contains("牛展 x2"));
        assertFalse(output.contains("牛展 x1\n\n牛展 x1"));
    }

    @Test
    void separatesSameSideDishWhenDemandDiffers() {
        String output = renderSides(
            side(1L, "黄瓜", "黄瓜"),
            side(2L, "黄瓜", "黄瓜"),
            side(3L, "黄瓜", "黄瓜 | 走花生")
        );

        assertTrue(output.contains("黄瓜 x2"));
        assertTrue(output.contains("黄瓜 x1\n走花生"));
    }

    @Test
    void groupsSameSideDishWithSameDemand() {
        String output = renderSides(
            side(1L, "黄瓜", "黄瓜 | 走花生"),
            side(2L, "黄瓜", "黄瓜 | 走花生")
        );

        assertTrue(output.contains("黄瓜 x2\n走花生"));
    }

    @Test
    void keepsSameSideDishSeparateWhenDemandsDiffer() {
        String output = renderSides(
            side(1L, "黄瓜", "黄瓜 | 走花生"),
            side(2L, "黄瓜", "黄瓜 | 加辣")
        );

        assertTrue(output.contains("黄瓜 x1\n走花生"));
        assertTrue(output.contains("黄瓜 x1\n加辣"));
    }

    @Test
    void keepsSingleRemoveOnionAsRemoveOnion() {
        String output = renderNoodle("走葱");

        assertTrue(output.contains("走葱"));
        assertFalse(output.contains("走青"));
    }

    @Test
    void keepsSingleRemoveCilantroAsRemoveCilantro() {
        String output = renderNoodle("走香菜");

        assertTrue(output.contains("走香菜"));
        assertFalse(output.contains("走青"));
    }

    @Test
    void combinesRemoveOnionAndCilantroOnlyWhenBothSelected() {
        String output = renderNoodle("走葱 走香菜");

        assertTrue(output.contains("走青"));
        assertFalse(output.contains("走葱"));
        assertFalse(output.contains("走香菜"));
    }

    @Test
    void keepsSingleAddOnionAsAddOnion() {
        String output = renderNoodle("加葱");

        assertTrue(output.contains("加葱"));
        assertFalse(output.contains("加青"));
    }

    @Test
    void keepsSingleAddCilantroAsAddCilantro() {
        String output = renderNoodle("加香菜");

        assertTrue(output.contains("加香菜"));
        assertFalse(output.contains("加青"));
    }

    @Test
    void combinesAddOnionAndCilantroOnlyWhenBothSelected() {
        String output = renderNoodle("加葱 加香菜");

        assertTrue(output.contains("加青"));
        assertFalse(output.contains("加葱"));
        assertFalse(output.contains("加香菜"));
    }

    @Test
    void keepsExtraBokChoyFullName() {
        String output = renderNoodle("加上海青");

        assertTrue(output.contains("加上海青"));
        assertFalse(output.contains("加青"));
    }

    @Test
    void doesNotCrossSimplifyAddAndRemoveGreenTokens() {
        String output = renderNoodle("走葱 加葱");

        assertTrue(output.contains("走葱"));
        assertTrue(output.contains("加葱"));
        assertFalse(output.contains("走青"));
        assertFalse(output.contains("加青"));
    }

    @Test
    void mergesAddOnModifierQuantitiesInGrabRenderer() {
        assertTrue(renderNoodle("+蛋 +蛋x2").contains("+蛋x3"));
        assertTrue(renderNoodle("+蛋 +蛋").contains("+蛋x2"));
        assertTrue(renderNoodle("+蛋x2 +蛋x3").contains("+蛋x5"));
        assertTrue(renderNoodle("+蛋 +煎x2").contains("+蛋 +煎x2"));
        assertFalse(renderNoodle("+蛋 +蛋x2").contains("+蛋 +蛋x2"));
    }

    @Test
    void mergesAddOnModifierQuantitiesInsidePrimaryInstructionOnly() {
        String output = renderNoodle("中 | +蛋 +蛋x2 +煎x2");

        assertTrue(output.contains("中 | +蛋x3 +煎x2 x1"));
        assertFalse(output.contains("+蛋 +蛋x2"));
    }

    @Test
    void supportsStarModifierQuantitySyntax() {
        String output = renderNoodle("中 | +蛋*2 +蛋");

        assertTrue(output.contains("中 | +蛋x3 x1"));
    }

    @Test
    void doesNotTreatSizeOrItemQuantityAsAddOnQuantity() {
        String output = renderNoodle("中 | +蛋 +蛋x2", 2);

        assertTrue(output.contains("中 | +蛋x3 x2"));
        assertFalse(output.contains("+蛋x6"));
    }

    @Test
    void grabReceiptDisplaysSplitTableSidesInChinese() {
        assertTrue(renderNoodle("中", 1, "T1-A").contains("桌号：T1-左"));
        assertTrue(renderNoodle("中", 1, "T1-B").contains("桌号：T1-右"));
    }

    @Test
    void frontdeskReceiptDoesNotApplyGrabGreenSimplification() {
        FrontdeskReceiptRenderer frontdeskRenderer = new FrontdeskReceiptRenderer();
        Order order = baseOrder();
        order.subtotal_amount = new BigDecimal("12.00");
        order.total_amount = new BigDecimal("13.80");

        OrderItem item = new OrderItem();
        item.id = 1L;
        item.order_id = order.id;
        item.item_name_snapshot_zh = "传统牛肉面";
        item.quantity = 1;
        item.unit_price = new BigDecimal("12.00");
        item.line_amount = new BigDecimal("12.00");
        item.status = "submitted";

        OrderItemOption removeOnion = new OrderItemOption();
        removeOnion.id = 1L;
        removeOnion.order_item_id = item.id;
        removeOnion.option_type_snapshot = "remove";
        removeOnion.option_name_snapshot_zh = "走葱";
        removeOnion.option_name_snapshot_en = "No Green Onion";
        removeOnion.price_delta = BigDecimal.ZERO;
        removeOnion.quantity = 1;

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.FRONTDESK_RECEIPT;
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = List.of(removeOnion);
        request.happened_at = order.submitted_at;

        String rawOutput = frontdeskRenderer.render(request);
        assertFalse(rawOutput.contains("FRONTDESK RECEIPT"));
        assertTrue(rawOutput.startsWith(PrintMarkup.LARGE_OPEN + "桌号: T1" + PrintMarkup.LARGE_CLOSE));
        assertFalse(rawOutput.contains(PrintMarkup.LARGE_OPEN + "桌号: T1" + PrintMarkup.LARGE_OPEN));
        assertEquals(1, countOccurrences(rawOutput, "1 x 传统牛肉面"));

        String output = stripMarkup(rawOutput);

        assertFalse(output.contains("走青"));
        assertFalse(output.contains("加青"));
    }

    @Test
    void frontdeskReceiptDisplaysSplitTableSidesInChinese() {
        FrontdeskReceiptRenderer frontdeskRenderer = new FrontdeskReceiptRenderer();
        Order order = baseOrder();
        order.table_no = "T1-B";
        order.subtotal_amount = new BigDecimal("12.00");
        order.total_amount = new BigDecimal("13.80");

        OrderItem item = new OrderItem();
        item.id = 1L;
        item.order_id = order.id;
        item.item_name_snapshot_zh = "传统牛肉面";
        item.quantity = 1;
        item.unit_price = new BigDecimal("12.00");
        item.line_amount = new BigDecimal("12.00");
        item.status = "submitted";

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.FRONTDESK_RECEIPT;
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = List.of();
        request.happened_at = order.submitted_at;

        String rawOutput = frontdeskRenderer.render(request);

        assertTrue(rawOutput.startsWith(PrintMarkup.LARGE_OPEN + "桌号: T1-右" + PrintMarkup.LARGE_CLOSE));
    }

    @Test
    void frontdeskReceiptDisplaysNoodleSpicyLevelFromStableOptionType() {
        FrontdeskReceiptRenderer frontdeskRenderer = new FrontdeskReceiptRenderer();
        Order order = baseOrder();
        order.subtotal_amount = new BigDecimal("12.00");
        order.total_amount = new BigDecimal("13.80");

        OrderItem item = new OrderItem();
        item.id = 1L;
        item.order_id = order.id;
        item.item_name_snapshot_zh = "传统牛肉面";
        item.quantity = 1;
        item.unit_price = new BigDecimal("12.00");
        item.line_amount = new BigDecimal("12.00");
        item.status = "submitted";

        OrderItemOption spicy = new OrderItemOption();
        spicy.id = 2L;
        spicy.order_item_id = item.id;
        spicy.option_type_snapshot = "spicy_level";
        spicy.option_group_snapshot = "SPICY_LEVEL";
        spicy.option_code_snapshot = "traditional_beef_noodle_spicy_level_extra";
        spicy.option_name_snapshot_zh = "加辣";
        spicy.option_name_snapshot_en = "Extra";
        spicy.price_delta = BigDecimal.ZERO;
        spicy.quantity = 1;

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.FRONTDESK_RECEIPT;
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = List.of(spicy);
        request.happened_at = order.submitted_at;

        String output = stripMarkup(frontdeskRenderer.render(request));

        assertTrue(output.contains("辣度: 加辣"));
    }

    @Test
    void frontdeskUpdateReceiptShowsOnlyAddedComboSideAndSideRemove() {
        FrontdeskReceiptRenderer frontdeskRenderer = new FrontdeskReceiptRenderer();
        Order order = baseOrder();
        order.subtotal_amount = new BigDecimal("100.00");
        order.total_amount = new BigDecimal("114.98");

        OrderItem addedItem = new OrderItem();
        addedItem.id = 2L;
        addedItem.order_id = order.id;
        addedItem.item_name_snapshot_zh = "传统牛肉面";
        addedItem.quantity = 1;
        addedItem.unit_price = new BigDecimal("21.00");
        addedItem.line_amount = new BigDecimal("21.00");
        addedItem.status = "submitted";
        addedItem.order_update_batch_id = 88L;

        OrderItemOption size = new OrderItemOption();
        size.id = 10L;
        size.order_item_id = addedItem.id;
        size.option_type_snapshot = "size";
        size.option_name_snapshot_zh = "大碗";
        size.option_name_snapshot_en = "Large";
        size.price_delta = BigDecimal.ZERO;
        size.quantity = 1;

        OrderItemOption combo = new OrderItemOption();
        combo.id = 11L;
        combo.order_item_id = addedItem.id;
        combo.option_type_snapshot = "addon";
        combo.option_group_snapshot = "COMBO";
        combo.option_code_snapshot = "combo";
        combo.option_name_snapshot_zh = "套餐";
        combo.option_name_snapshot_en = "Combo";
        combo.price_delta = new BigDecimal("5.00");
        combo.quantity = 1;

        OrderItemOption comboSide = new OrderItemOption();
        comboSide.id = 12L;
        comboSide.option_id = 201L;
        comboSide.order_item_id = addedItem.id;
        comboSide.option_type_snapshot = "addon";
        comboSide.option_group_snapshot = "COMBO_SIDE";
        comboSide.option_code_snapshot = "combo_cucumber_salad";
        comboSide.option_name_snapshot_zh = "套餐拌黄瓜";
        comboSide.option_name_snapshot_en = "Combo Cucumber Salad";
        comboSide.price_delta = BigDecimal.ZERO;
        comboSide.quantity = 1;

        OrderItemOption sideRemove = new OrderItemOption();
        sideRemove.id = 13L;
        sideRemove.order_item_id = addedItem.id;
        sideRemove.option_type_snapshot = "remove";
        sideRemove.option_group_snapshot = "COMBO_SIDE_REMOVE";
        sideRemove.parent_option_id_snapshot = comboSide.option_id;
        sideRemove.option_name_snapshot_zh = "走花生";
        sideRemove.option_name_snapshot_en = "No Peanut";
        sideRemove.price_delta = BigDecimal.ZERO;
        sideRemove.quantity = 1;

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.FRONTDESK_RECEIPT;
        request.order = order;
        request.order_items = List.of(addedItem);
        request.order_item_options = List.of(size, combo, comboSide, sideRemove);
        request.happened_at = order.submitted_at;
        request.is_update_ticket = true;
        request.order_update_batch_id = 88L;

        String output = stripMarkup(frontdeskRenderer.render(request));

        assertTrue(output.contains("UPDATED"));
        assertTrue(output.contains("Added items only"));
        assertTrue(output.contains("1 x Combo 传统牛肉面 Large"));
        assertTrue(output.contains("小菜: 拌黄瓜"));
        assertTrue(output.contains("走花生"));
        assertTrue(output.contains("Subtotal: $21.00"));
        assertTrue(output.contains("Tax (14.975%): $3.14"));
        assertTrue(output.contains("Total: $24.14"));
        assertFalse(output.contains("Subtotal: $100.00"));
    }

    private String renderSides(SideCase... sideCases) {
        Order order = baseOrder();

        List<OrderItem> items = new ArrayList<>();
        List<KitchenTask> tasks = new ArrayList<>();
        for (SideCase sideCase : sideCases) {
            OrderItem item = new OrderItem();
            item.id = sideCase.id();
            item.order_id = order.id;
            item.item_name_snapshot_zh = sideCase.itemName();
            item.category_code_snapshot = "SIDE";
            item.quantity = 1;
            item.status = "submitted";
            items.add(item);

            KitchenTask task = new KitchenTask();
            task.id = sideCase.id();
            task.order_id = order.id;
            task.order_item_id = item.id;
            task.store_id = order.store_id;
            task.station_code = "COLD";
            task.item_name_snapshot_zh = sideCase.itemName();
            task.special_instructions_snapshot = sideCase.specialInstructions();
            task.status = "pending";
            task.quantity = 1;
            task.created_at = LocalDateTime.of(2026, 6, 16, 12, 0).plusSeconds(sideCase.id());
            tasks.add(task);
        }

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.GRAB;
        request.order = order;
        request.order_items = items;
        request.kitchen_tasks = tasks;
        request.happened_at = order.submitted_at;
        return stripMarkup(renderer.render(request));
    }

    private String renderNoodle(String specialInstructions) {
        return renderNoodle(specialInstructions, 1);
    }

    private String renderNoodle(String specialInstructions, int quantity) {
        return renderNoodle(specialInstructions, quantity, "T1");
    }

    private String renderNoodle(String specialInstructions, int quantity, String tableNo) {
        Order order = baseOrder();
        order.table_no = tableNo;

        OrderItem item = new OrderItem();
        item.id = 1L;
        item.order_id = order.id;
        item.item_name_snapshot_zh = "传统牛肉面";
        item.category_code_snapshot = "SOUP_NOODLE";
        item.quantity = quantity;
        item.status = "submitted";

        KitchenTask task = new KitchenTask();
        task.id = 1L;
        task.order_id = order.id;
        task.order_item_id = item.id;
        task.store_id = order.store_id;
        task.station_code = "NOODLE";
        task.item_name_snapshot_zh = "传统牛肉面";
        task.special_instructions_snapshot = specialInstructions;
        task.status = "pending";
        task.quantity = quantity;
        task.created_at = LocalDateTime.of(2026, 6, 16, 12, 0);

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.GRAB;
        request.order = order;
        request.order_items = List.of(item);
        request.kitchen_tasks = List.of(task);
        request.happened_at = order.submitted_at;
        return stripMarkup(renderer.render(request));
    }

    private Order baseOrder() {
        Order order = new Order();
        order.id = 100L;
        order.store_id = 1L;
        order.order_type = "dine_in";
        order.table_no = "T1";
        order.submitted_at = LocalDateTime.of(2026, 6, 16, 12, 23);
        return order;
    }

    private SideCase side(Long id, String itemName, String specialInstructions) {
        return new SideCase(id, itemName, specialInstructions);
    }

    private String stripMarkup(String value) {
        return value
            .replace(PrintMarkup.DOUBLE_HEIGHT_OPEN, "")
            .replace(PrintMarkup.DOUBLE_HEIGHT_CLOSE, "")
            .replace(PrintMarkup.LARGE_OPEN, "")
            .replace(PrintMarkup.LARGE_CLOSE, "")
            .replace(PrintMarkup.SMALL_OPEN, "")
            .replace(PrintMarkup.SMALL_CLOSE, "");
    }

    private int countOccurrences(String value, String needle) {
        int count = 0;
        int start = 0;
        while (true) {
            int index = value.indexOf(needle, start);
            if (index < 0) {
                return count;
            }
            count++;
            start = index + needle.length();
        }
    }

    private record SideCase(Long id, String itemName, String specialInstructions) {
    }
}
