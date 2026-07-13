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
    void groupsTwoIdenticalFriedItemsWithStableFirstOccurrenceOrder() {
        String output = renderFried(
            fried(1L, 300L, "炸虾", null, null, null),
            fried(2L, 300L, "炸虾", null, null, null),
            fried(3L, 301L, "春卷", null, null, null)
        );

        assertTrue(output.contains("2*炸虾"));
        assertTrue(output.contains("1*春卷"));
        assertTrue(output.indexOf("2*炸虾") < output.indexOf("1*春卷"));
        assertFalse(output.contains("1*炸虾\n\n1*炸虾"));
    }

    @Test
    void groupsThreeIdenticalFriedItems() {
        String output = renderFried(
            fried(1L, 300L, "炸虾", null, null, null),
            fried(2L, 300L, "炸虾", null, null, null),
            fried(3L, 300L, "炸虾", null, null, null)
        );

        assertTrue(output.contains("3*炸虾"));
        assertEquals(1, countOccurrences(output, "炸虾"));
    }

    @Test
    void keepsFriedItemsSeparateWhenNotesOrOptionsDiffer() {
        String output = renderFried(
            fried(1L, 300L, "炸虾", null, null, null),
            fried(2L, 300L, "炸虾", "不要酱", null, null),
            fried(3L, 300L, "炸虾", null, "SPICY_LEVEL", "spicy_mild"),
            fried(4L, 300L, "炸虾", null, "SPICY_LEVEL", "spicy_extra"),
            fried(5L, 300L, "炸虾", null, "COMBO_EGG", "combo_tea_egg"),
            fried(6L, 300L, "炸虾", null, "COMBO_EGG", "combo_fried_egg")
        );

        assertEquals(6, countOccurrences(output, "1*炸虾"));
        assertTrue(output.contains("备注：不要酱"));
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
        assertTrue(renderNoodle("+蛋 +蛋x2").contains("+蛋×3"));
        assertTrue(renderNoodle("+蛋 +蛋").contains("+蛋×2"));
        assertTrue(renderNoodle("+蛋x2 +蛋x3").contains("+蛋×5"));
        assertTrue(renderNoodle("+蛋 +煎x2").contains("+蛋 +煎×2"));
        assertFalse(renderNoodle("+蛋 +蛋x2").contains("+蛋 +蛋x2"));
    }

    @Test
    void mergesAddOnModifierQuantitiesInsidePrimaryInstructionOnly() {
        String output = renderNoodle("中 | +蛋 +蛋x2 +煎x2");

        assertTrue(output.contains("中×1 | +蛋×3 +煎×2"));
        assertFalse(output.contains("+蛋 +蛋x2"));
    }

    @Test
    void supportsStarModifierQuantitySyntax() {
        String output = renderNoodle("中 | +蛋*2 +蛋");

        assertTrue(output.contains("中×1 | +蛋×3"));
    }

    @Test
    void doesNotTreatSizeOrItemQuantityAsAddOnQuantity() {
        String output = renderNoodle("中 | +蛋 +蛋x2", 2);

        assertTrue(output.contains("(中 | +蛋×3) ×2"));
        assertFalse(output.contains("+蛋×6"));
    }

    @Test
    void groupsTwoIdenticalNoodlesAsSingleBowlConfigTimesQuantity() {
        String output = renderNoodles(
            noodle(1L, "中酸 | +蛋"),
            noodle(2L, "中酸 | +蛋")
        );

        assertTrue(output.contains("(中酸 | +蛋) ×2"));
        assertFalse(output.contains("中酸 | +蛋 x2"));
    }

    @Test
    void groupsThreeIdenticalNoodlesAsSingleBowlConfigTimesQuantity() {
        String output = renderNoodles(
            noodle(1L, "中酸 | +蛋"),
            noodle(2L, "中酸 | +蛋"),
            noodle(3L, "中酸 | +蛋")
        );

        assertTrue(output.contains("(中酸 | +蛋) ×3"));
    }

    @Test
    void singleNoodleShowsBowlQuantitySeparatelyFromEggQuantity() {
        String output = renderNoodle("中酸 | +蛋");

        assertTrue(output.contains("中酸×1 | +蛋"));
        assertFalse(output.contains("+蛋 x2"));
        assertFalse(output.contains("(中酸 | +蛋) ×1"));
    }

    @Test
    void singleNoodleWithTwoEggsKeepsEggQuantityInsideConfig() {
        String output = renderNoodle("中酸 | +蛋 +蛋");

        assertTrue(output.contains("中酸×1 | +蛋×2"));
        assertFalse(output.contains("(中酸 | +蛋×2) ×1"));
    }

    @Test
    void groupsTwoIdenticalNoodlesWithTwoEggsByBowlQuantity() {
        String output = renderNoodles(
            noodle(1L, "中酸 | +蛋 +蛋"),
            noodle(2L, "中酸 | +蛋 +蛋")
        );

        assertTrue(output.contains("(中酸 | +蛋×2) ×2"));
    }

    @Test
    void doesNotMergeNoodlesWithDifferentSpicyLevels() {
        String output = renderNoodles(
            noodle(1L, "中酸 | +蛋"),
            noodle(2L, "中辣 | +蛋")
        );

        assertTrue(output.contains("中酸×1 | +蛋"));
        assertTrue(output.contains("中辣×1 | +蛋"));
        assertFalse(output.contains("(中酸 | +蛋) ×2"));
        assertFalse(output.contains("(中辣 | +蛋) ×2"));
    }

    @Test
    void doesNotMergeNoodlesWithDifferentRemoveOrNotes() {
        String output = renderNoodles(
            noodle(1L, "中酸 | +蛋", "少汤"),
            noodle(2L, "中酸 | 不要葱 | +蛋")
        );

        assertTrue(output.contains("中酸×1 | +蛋 | 备注：少汤"));
        assertTrue(output.contains("中酸×1 | 不要葱 | +蛋"));
        assertFalse(output.contains(") ×2"));
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
        item.category_code_snapshot = "SOUP_NOODLE";
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

        OrderItemOption size = option(2L, item.id, "size", "SIZE", "size_regular", "中碗");
        size.option_name_snapshot_en = "regular";

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.FRONTDESK_RECEIPT;
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = List.of(removeOnion, size);
        request.happened_at = order.submitted_at;

        String rawOutput = frontdeskRenderer.render(request);
        assertFalse(rawOutput.contains("FRONTDESK RECEIPT"));
        assertTrue(rawOutput.startsWith(PrintMarkup.LARGE_OPEN + "桌号: T1" + PrintMarkup.LARGE_CLOSE));
        assertFalse(rawOutput.contains(PrintMarkup.LARGE_OPEN + "桌号: T1" + PrintMarkup.LARGE_OPEN));
        assertEquals(1, countOccurrences(rawOutput, "中碗牛肉面"));
        assertFalse(rawOutput.toLowerCase().contains("regular"));
        assertFalse(rawOutput.contains("传统牛肉面"));
        assertFalse(rawOutput.contains("*combo"));
        assertEquals("传统牛肉面", item.item_name_snapshot_zh);

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
    void frontdeskReceiptFormatsSoupNoodleComboWithQuantityAndNoEgg() {
        FrontdeskReceiptRenderer frontdeskRenderer = new FrontdeskReceiptRenderer();
        Order order = baseOrder();
        order.subtotal_amount = new BigDecimal("42.00");
        order.total_amount = new BigDecimal("48.29");

        OrderItem item = new OrderItem();
        item.id = 1L;
        item.order_id = order.id;
        item.category_code_snapshot = "SOUP_NOODLE";
        item.item_name_snapshot_zh = "传统牛肉面";
        item.quantity = 2;
        item.unit_price = new BigDecimal("21.00");
        item.line_amount = new BigDecimal("42.00");
        item.notes = "少汤";
        item.status = "submitted";

        OrderItemOption combo = option(1L, item.id, "addon", "COMBO", "combo", "套餐");
        combo.price_delta = new BigDecimal("5.00");
        OrderItemOption spicy = option(2L, item.id, "spicy_level", "SPICY_LEVEL", "spicy_mild", "少辣");
        OrderItemOption side = option(3L, item.id, "addon", "COMBO_SIDE", "combo_edamame", "套餐毛豆");
        OrderItemOption size = option(4L, item.id, "size", "SIZE", "size_regular", "中碗");
        size.option_name_snapshot_en = "REGULAR";

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.FRONTDESK_RECEIPT;
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = List.of(combo, spicy, side, size);
        request.happened_at = order.submitted_at;

        String output = stripMarkup(frontdeskRenderer.render(request));

        assertTrue(output.contains("2* combo 中碗牛肉面"));
        assertFalse(output.toLowerCase().contains("regular"));
        assertTrue(output.contains("辣度: 少辣"));
        assertTrue(output.contains("走蛋"));
        assertTrue(output.contains("小菜: 毛豆"));
        assertTrue(output.contains("备注：少汤"));
        assertFalse(output.contains("传统牛肉面"));
        assertEquals("传统牛肉面", item.item_name_snapshot_zh);
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
        addedItem.category_code_snapshot = "SOUP_NOODLE";
        addedItem.item_name_snapshot_zh = "传统牛肉面";
        addedItem.quantity = 1;
        addedItem.unit_price = new BigDecimal("21.00");
        addedItem.line_amount = new BigDecimal("21.00");
        addedItem.notes = "少汤";
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

        OrderItemOption comboEgg = new OrderItemOption();
        comboEgg.id = 12L;
        comboEgg.order_item_id = addedItem.id;
        comboEgg.option_type_snapshot = "addon";
        comboEgg.option_group_snapshot = "COMBO_EGG";
        comboEgg.option_code_snapshot = "combo_fried_egg";
        comboEgg.option_name_snapshot_zh = "套餐煎蛋";
        comboEgg.option_name_snapshot_en = "Combo Fried Egg";
        comboEgg.price_delta = BigDecimal.ZERO;
        comboEgg.quantity = 1;

        OrderItemOption comboSide = new OrderItemOption();
        comboSide.id = 13L;
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
        sideRemove.id = 14L;
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
        request.order_item_options = List.of(size, combo, comboEgg, comboSide, sideRemove);
        request.happened_at = order.submitted_at;
        request.is_update_ticket = true;
        request.order_update_batch_id = 88L;

        String output = stripMarkup(frontdeskRenderer.render(request));

        assertTrue(output.contains("UPDATED"));
        assertTrue(output.contains("Added items only"));
        assertTrue(output.contains("1* combo 大碗牛肉面"));
        assertFalse(output.toLowerCase().contains("large"));
        assertTrue(output.contains("鸡蛋: 煎蛋"));
        assertTrue(output.contains("小菜: 拌黄瓜"));
        assertTrue(output.contains("走花生"));
        assertTrue(output.contains("备注：少汤"));
        assertTrue(output.contains("Subtotal: $21.00"));
        assertTrue(output.contains("Tax (14.975%): $3.14"));
        assertTrue(output.contains("Total: $24.14"));
        assertFalse(output.contains("Subtotal: $100.00"));
    }

    @Test
    void frontdeskReceiptDoesNotDuplicateBowlSizeAlreadyInSoupNoodleName() {
        FrontdeskReceiptRenderer frontdeskRenderer = new FrontdeskReceiptRenderer();
        Order order = baseOrder();
        order.subtotal_amount = new BigDecimal("12.00");
        order.total_amount = new BigDecimal("13.80");

        OrderItem item = new OrderItem();
        item.id = 1L;
        item.order_id = order.id;
        item.category_code_snapshot = "SOUP_NOODLE";
        item.item_name_snapshot_zh = "中碗传统牛肉面";
        item.quantity = 1;
        item.unit_price = new BigDecimal("12.00");
        item.line_amount = new BigDecimal("12.00");
        item.status = "submitted";

        OrderItemOption size = option(1L, item.id, "size", "SIZE", "size_regular", "中碗");
        size.option_name_snapshot_en = "Regular";

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.FRONTDESK_RECEIPT;
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = List.of(size);
        request.happened_at = order.submitted_at;

        String output = stripMarkup(frontdeskRenderer.render(request));

        assertTrue(output.contains("中碗牛肉面"));
        assertFalse(output.contains("中碗中碗"));
        assertFalse(output.contains("传统牛肉面"));
    }

    @Test
    void frontdeskReceiptFormatsRegularAndLargeBeefNoodleLinesWithExactComboPrefixes() {
        FrontdeskReceiptRenderer frontdeskRenderer = new FrontdeskReceiptRenderer();
        Order order = baseOrder();
        order.subtotal_amount = new BigDecimal("96.00");
        order.total_amount = new BigDecimal("110.38");

        OrderItem regular = frontdeskSoupNoodleItem(1L, 1, "传统牛肉面");
        OrderItem large = frontdeskSoupNoodleItem(2L, 1, "传统牛肉面");
        OrderItem regularCombo = frontdeskSoupNoodleItem(3L, 1, "传统牛肉面");
        OrderItem largeCombo = frontdeskSoupNoodleItem(4L, 2, "传统牛肉面");

        List<OrderItemOption> options = List.of(
            option(1L, regular.id, "size", "SIZE", "size_regular", "中碗"),
            option(2L, large.id, "size", "SIZE", "size_large", "大碗"),
            option(3L, regularCombo.id, "size", "SIZE", "size_regular", "中碗"),
            option(4L, regularCombo.id, "addon", "COMBO", "combo", "套餐"),
            option(5L, largeCombo.id, "size", "SIZE", "size_large", "大碗"),
            option(6L, largeCombo.id, "addon", "COMBO", "combo", "套餐")
        );

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.FRONTDESK_RECEIPT;
        request.order = order;
        request.order_items = List.of(regular, large, regularCombo, largeCombo);
        request.order_item_options = options;
        request.happened_at = order.submitted_at;

        List<String> outputLines = stripMarkup(frontdeskRenderer.render(request)).lines().toList();

        assertTrue(outputLines.contains("中碗牛肉面"));
        assertTrue(outputLines.contains("大碗牛肉面"));
        assertTrue(outputLines.contains("1* combo 中碗牛肉面"));
        assertTrue(outputLines.contains("2* combo 大碗牛肉面"));
        assertFalse(outputLines.stream().anyMatch(line -> line.toLowerCase().contains("regular") || line.toLowerCase().contains("large")));
    }

    private OrderItem frontdeskSoupNoodleItem(Long id, int quantity, String nameZh) {
        OrderItem item = new OrderItem();
        item.id = id;
        item.order_id = 100L;
        item.category_code_snapshot = "SOUP_NOODLE";
        item.item_name_snapshot_zh = nameZh;
        item.quantity = quantity;
        item.unit_price = new BigDecimal("12.00");
        item.line_amount = new BigDecimal("12.00").multiply(BigDecimal.valueOf(quantity));
        item.status = "submitted";
        return item;
    }

    private String renderFried(FriedCase... friedCases) {
        Order order = baseOrder();
        List<OrderItem> items = new ArrayList<>();
        List<KitchenTask> tasks = new ArrayList<>();
        List<OrderItemOption> options = new ArrayList<>();

        for (FriedCase friedCase : friedCases) {
            OrderItem item = new OrderItem();
            item.id = friedCase.id();
            item.order_id = order.id;
            item.menu_item_id = friedCase.menuItemId();
            item.item_name_snapshot_zh = friedCase.itemName();
            item.category_code_snapshot = "FRIED";
            item.combo_role = friedCase.comboRole();
            item.notes = friedCase.note();
            item.quantity = 1;
            item.status = "submitted";
            items.add(item);

            KitchenTask task = new KitchenTask();
            task.id = friedCase.id();
            task.order_id = order.id;
            task.order_item_id = item.id;
            task.store_id = order.store_id;
            task.station_code = "DEEPFRIED";
            task.item_name_snapshot_zh = friedCase.itemName();
            task.status = "pending";
            task.quantity = 1;
            task.created_at = LocalDateTime.of(2026, 6, 16, 12, 0).plusSeconds(friedCase.id());
            tasks.add(task);

            if (friedCase.optionCode() != null) {
                OrderItemOption option = option(
                    100L + friedCase.id(),
                    item.id,
                    "addon",
                    friedCase.optionGroup(),
                    friedCase.optionCode(),
                    friedCase.optionCode()
                );
                option.option_id = (long) friedCase.optionCode().hashCode();
                options.add(option);
            }
        }

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.GRAB;
        request.order = order;
        request.order_items = items;
        request.order_item_options = options;
        request.kitchen_tasks = tasks;
        request.happened_at = order.submitted_at;
        return stripMarkup(renderer.render(request));
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

    private String renderNoodles(NoodleCase... noodleCases) {
        Order order = baseOrder();
        List<OrderItem> items = new ArrayList<>();
        List<KitchenTask> tasks = new ArrayList<>();

        for (NoodleCase noodleCase : noodleCases) {
            OrderItem item = new OrderItem();
            item.id = noodleCase.id();
            item.order_id = order.id;
            item.menu_item_id = noodleCase.menuItemId();
            item.item_name_snapshot_zh = "酸菜牛肉面";
            item.category_code_snapshot = "SOUP_NOODLE";
            item.quantity = noodleCase.quantity();
            item.notes = noodleCase.note();
            item.status = "submitted";
            items.add(item);

            KitchenTask task = new KitchenTask();
            task.id = noodleCase.id();
            task.order_id = order.id;
            task.order_item_id = item.id;
            task.store_id = order.store_id;
            task.station_code = "NOODLE";
            task.item_name_snapshot_zh = "酸菜牛肉面";
            task.special_instructions_snapshot = noodleCase.specialInstructions();
            task.status = "pending";
            task.quantity = noodleCase.quantity();
            task.created_at = LocalDateTime.of(2026, 6, 16, 12, 0).plusSeconds(noodleCase.id());
            tasks.add(task);
        }

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.GRAB;
        request.order = order;
        request.order_items = items;
        request.order_item_options = List.of();
        request.kitchen_tasks = tasks;
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

    private NoodleCase noodle(Long id, String specialInstructions) {
        return noodle(id, specialInstructions, null);
    }

    private NoodleCase noodle(Long id, String specialInstructions, String note) {
        return new NoodleCase(id, 200L, specialInstructions, note, 1);
    }

    private FriedCase fried(
        Long id,
        Long menuItemId,
        String itemName,
        String note,
        String optionGroup,
        String optionCode
    ) {
        return new FriedCase(id, menuItemId, itemName, note, optionGroup, optionCode, "standalone");
    }

    private OrderItemOption option(
        Long id,
        Long orderItemId,
        String optionType,
        String optionGroup,
        String optionCode,
        String nameZh
    ) {
        OrderItemOption option = new OrderItemOption();
        option.id = id;
        option.order_item_id = orderItemId;
        option.option_type_snapshot = optionType;
        option.option_group_snapshot = optionGroup;
        option.option_code_snapshot = optionCode;
        option.option_name_snapshot_zh = nameZh;
        option.option_name_snapshot_en = nameZh;
        option.price_delta = BigDecimal.ZERO;
        option.quantity = 1;
        return option;
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

    private record NoodleCase(Long id, Long menuItemId, String specialInstructions, String note, int quantity) {
    }

    private record FriedCase(
        Long id,
        Long menuItemId,
        String itemName,
        String note,
        String optionGroup,
        String optionCode,
        String comboRole
    ) {
    }
}
