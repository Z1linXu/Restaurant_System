package com.restaurant.system.printing.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.dto.ReceiptPresentation;
import com.restaurant.system.user.entity.Store;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AlloReceiptPresentationTest {
    private final GrabReceiptRenderer grab = new GrabReceiptRenderer();
    private final FrontdeskReceiptRenderer frontdesk = new FrontdeskReceiptRenderer();

    @Test
    void nullContextRetainsExactLegacyFrontdeskBytesIncludingFreeCustomOmissionAndCents() {
        PrintRenderRequest request = request();
        request.receipt_context = null;
        OrderItem item = request.order_items.get(0);
        item.item_name_snapshot_zh = "Legacy item";
        item.quantity = 2;
        item.line_amount = new BigDecimal("35.99");
        request.order.subtotal_amount = item.line_amount;
        request.order.total_amount = item.line_amount;
        request.order_item_options = List.of(option(20L, "CUSTOM", "0.00", 1));
        String expected = PrintMarkup.TOP_FEED_16MM + "[[LARGE]]T1[[/LARGE]]\n"
            + sized("--------------------------------")
            + sized("1 x Legacy item") + sized("$18.00") + "\n"
            + sized("1 x Legacy item") + sized("$17.99") + "\n"
            + sized("--------------------------------") + sized("Subtotal: $35.99")
            + sized("Tax (14.975%): $0.00") + sized("Total: $35.99")
            + sized("--------------------------------") + sized("Submitted: 2026-11-03 12:34") + "\n";
        assertEquals(expected, frontdesk.render(request));
    }

    @Test
    void nullContextStillExcludesBackKitchenFromLegacyGrab() {
        PrintRenderRequest request = request();
        request.receipt_context = null;
        assertEquals("", grab.render(request));
    }

    @Test
    void assignedRealKitchenTaskUsesCapturedNamesAndActualOptionQuantitiesOnce() {
        PrintRenderRequest request = request();
        request.receipt_context.language = "fr-CA";
        request.receipt_context.item_names = Map.of("10", "Repas figé");
        request.receipt_context.option_names = Map.of("20", "Choix gratuit", "21", "Supplément");
        request.order_items.get(0).quantity = 3;
        request.order_items.get(0).notes = "Keep  two spaces / sans sauce";
        request.kitchen_tasks.get(0).quantity = 3;
        request.kitchen_tasks.get(0).special_instructions_snapshot = "LEGACY_NOODLE_ABBREVIATION";
        request.order_item_options = List.of(option(20L, "CUSTOM", "0.00", 1), option(21L, "ADD_ON", "7.99", 2));
        String rendered = grab.render(request);
        assertTrue(rendered.contains("Repas figé x3"));
        assertTrue(rendered.contains("Choix gratuit x3"));
        assertTrue(rendered.contains("Supplément x6"));
        assertTrue(rendered.contains("Notes: Keep  two spaces / sans sauce"));
        assertFalse(rendered.contains("LEGACY_NOODLE_ABBREVIATION"));
        assertFalse(rendered.contains("Live restaurant name"));
        assertEquals(1, occurrences(rendered, PrintMarkup.TOP_FEED_16MM));
        assertEquals(1, occurrences(rendered, "Repas figé"));
    }

    @Test
    void unassignedKitchenTaskFailsInsteadOfSilentlyUsingLegacyAllowlistOrDroppingIt() {
        PrintRenderRequest request = request();
        for (String station : List.of("NOODLE", "UNASSIGNED_STATION")) {
            request.kitchen_tasks.get(0).station_code = station;
            BusinessException error = assertThrows(BusinessException.class, () -> grab.render(request));
            assertEquals("RECEIPT_STATION_NOT_ASSIGNED: " + station, error.getMessage());
        }
    }

    @Test
    void cancelledUnassignedTaskDoesNotBlockTheValidEventTask() {
        PrintRenderRequest request = request();
        KitchenTask cancelled = task(2L, 10L, "UNASSIGNED_STATION");
        cancelled.status = "cancelled";
        request.kitchen_tasks = List.of(request.kitchen_tasks.get(0), cancelled);
        String rendered = grab.render(request);
        assertEquals(1, occurrences(rendered, "Frozen meal x1"));
    }

    @Test
    void initialContextExcludesLaterUpdateItemsAndTheirTasksAndKeepsOriginalTotals() {
        PrintRenderRequest request = request();
        OrderItem later = item(11L, "100.00");
        later.item_name_snapshot_zh = "LATER_BATCH_MUST_NOT_APPEAR";
        request.order_items = List.of(request.order_items.get(0), later);
        request.kitchen_tasks = List.of(request.kitchen_tasks.get(0), task(2L, 11L, "UNASSIGNED_STATION"));
        request.order.subtotal_amount = new BigDecimal("119.99");
        request.order.total_amount = new BigDecimal("137.96");
        String front = plain(frontdesk.render(request));
        assertTrue(front.contains("Subtotal: $19.99"));
        assertTrue(front.contains("Total: $22.98"));
        assertFalse(front.contains("LATER_BATCH"));
        assertFalse(front.contains("$119.99"));
        assertFalse(grab.render(request).contains("LATER_BATCH"));
    }

    @Test
    void frontdeskPrintsRealFreeCustomRemoveAndPaidSelectionsExactlyOnceWithoutSemanticSpoofing() {
        PrintRenderRequest request = request();
        request.order_items.get(0).line_amount = new BigDecimal("27.98");
        request.receipt_context.subtotal = new BigDecimal("27.98");
        request.receipt_context.total = new BigDecimal("32.17");
        OrderItemOption custom = option(20L, "CUSTOM", "0.00", 1);
        custom.option_code_snapshot = "spicy_named_but_actual_custom";
        request.order_item_options = List.of(custom, option(21L, "ADD_ON", "7.99", 1), option(22L, "REMOVE", "0.00", 1));
        request.receipt_context.option_names = Map.of("20", "Actual custom", "21", "Actual paid add-on", "22", "Actual remove");
        String rendered = frontdesk.render(request);
        for (String label : List.of("Actual custom x1", "Actual paid add-on x1", "Actual remove x1")) {
            assertEquals(1, occurrences(rendered, label));
        }
        assertFalse(rendered.contains("辣度"));
        assertFalse(rendered.contains("Combo"));
        assertTrue(plain(rendered).contains("$27.98"));
        assertEquals("0.00", custom.price_delta.toPlainString());
        assertEquals("CUSTOM", custom.option_group_snapshot);
    }

    @Test
    void frontdeskKeepsPersistedCentAllocationAndPerItemOptionQuantity() {
        PrintRenderRequest request = request();
        OrderItem item = request.order_items.get(0);
        item.quantity = 2;
        item.line_amount = new BigDecimal("35.99");
        request.receipt_context.subtotal = item.line_amount;
        request.receipt_context.total = item.line_amount;
        request.order_item_options = List.of(option(20L, "ADD_ON", "7.99", 2));
        request.receipt_context.option_names = Map.of("20", "Selected add-on");
        String rendered = plain(frontdesk.render(request));
        assertEquals(List.of("$18.00", "$17.99"), rendered.lines().filter(line -> line.startsWith("$")).toList());
        assertEquals(2, occurrences(rendered, "Selected add-on x2"));
        assertFalse(rendered.contains("Selected add-on x4"));
        assertEquals(new BigDecimal("35.99"), item.line_amount);
        assertEquals(new BigDecimal("19.99"), item.unit_price);
    }

    @Test
    void updateKeepsExistingAddedBatchTaxCalculationInsteadOfWholeOrderOrInitialTotals() {
        PrintRenderRequest request = request();
        request.is_update_ticket = true;
        request.order.order_type = "takeout";
        OrderItem added = item(11L, "7.99");
        request.order_items = List.of(request.order_items.get(0), added);
        request.receipt_context.order_item_ids = List.of(11L);
        request.receipt_context.item_names = Map.of("11", "Added meal");
        request.receipt_context.subtotal = new BigDecimal("999.00");
        request.receipt_context.total = new BigDecimal("999.00");
        String rendered = plain(frontdesk.render(request));
        assertTrue(rendered.contains("UPDATED"));
        assertTrue(rendered.contains("Added items only"));
        assertTrue(rendered.contains("Subtotal: $7.99"));
        assertTrue(rendered.contains("Tax (14.975%): $1.20"));
        assertTrue(rendered.contains("Total: $9.19"));
        assertFalse(rendered.contains("Frozen meal"));
        assertFalse(rendered.contains("$999.00"));
    }

    @ParameterizedTest
    @CsvSource({"zh-CN,小计,税,外带", "en,Subtotal,Tax,Takeout", "fr-CA,Sous-total,Taxes,À emporter"})
    void fixedLabelsUseCapturedPrintLanguageAndNotesRemainUntranslated(String language, String subtotal, String tax, String takeout) {
        PrintRenderRequest request = request();
        request.receipt_context.language = language;
        request.order.order_type = "takeout";
        request.order.pickup_no = "P7";
        String note = "Keep  double spaces / ne pas traduire";
        request.order_items.get(0).notes = note;
        String rendered = plain(frontdesk.render(request));
        assertTrue(rendered.contains(subtotal + ": $19.99"));
        assertTrue(rendered.contains(tax + " (14.975%): $2.99"));
        assertTrue(rendered.contains(takeout));
        assertTrue(rendered.contains(note));
        assertTrue(rendered.contains("Frozen restaurant"));
        assertTrue(grab.render(request).contains(takeout));
        assertEquals("Frozen meal", request.receipt_context.item_names.get("10"));
    }

    @Test
    void unknownContextLanguageVersionAndMissingInitialTotalsFailClosed() {
        PrintRenderRequest request = request();
        request.receipt_context.language = "fr";
        assertThrows(BusinessException.class, () -> frontdesk.render(request));
        assertThrows(BusinessException.class, () -> grab.render(request));
        request.receipt_context.language = "en";
        request.receipt_context.version = "UNKNOWN";
        assertThrows(BusinessException.class, () -> frontdesk.render(request));
        request.receipt_context.version = "ALLO_RECEIPT_V1";
        request.receipt_context.subtotal = null;
        BusinessException error = assertThrows(BusinessException.class, () -> frontdesk.render(request));
        assertEquals("RECEIPT_FROZEN_TOTALS_REQUIRED", error.getMessage());
    }

    @Test
    void missingCapturedTranslationFallsBackOnlyToFrozenOrderSnapshot() {
        PrintRenderRequest request = request();
        request.receipt_context.language = "fr-CA";
        request.receipt_context.item_names = Map.of();
        request.order_items.get(0).item_name_snapshot_fr = null;
        request.order_items.get(0).item_name_snapshot_en = "Frozen English fallback";
        assertTrue(frontdesk.render(request).contains("Frozen English fallback"));
        assertTrue(grab.render(request).contains("Frozen English fallback"));
    }

    @Test void frozenCategoryExclusionsAffectOnlyGrabNotReceiptAndNeverDuplicateTheSharedStation() {
        var request=request();
        var dessert=item(11L,"3.99");dessert.category_code_snapshot="DESSERT";
        var drink=item(12L,"1.99");drink.category_code_snapshot="DRINK";
        request.order_items=List.of(request.order_items.get(0),dessert,drink);
        request.kitchen_tasks=List.of(task(1L,10L,"BACK_KITCHEN"),task(2L,11L,"BACK_KITCHEN"));
        request.receipt_context.order_item_ids=List.of(10L,11L,12L);
        request.receipt_context.item_names=Map.of("10","B1 Main","11","E1 Dessert","12","D1 Water");
        request.receipt_context.kitchen_excluded_item_ids=List.of(11L,12L);
        String kitchen=grab.render(request);String receipt=frontdesk.render(request);
        assertTrue(kitchen.contains("B1 Main"));assertFalse(kitchen.contains("E1 Dessert"));assertFalse(kitchen.contains("D1 Water"));
        assertTrue(receipt.contains("B1 Main"));assertTrue(receipt.contains("E1 Dessert"));assertTrue(receipt.contains("D1 Water"));
        request.receipt_context.kitchen_excluded_item_ids=List.of(12L);
        assertEquals(1,occurrences(grab.render(request),"E1 Dessert"));
    }

    private PrintRenderRequest request() {
        PrintRenderRequest request = new PrintRenderRequest();
        request.order = new Order();
        request.order.id = 1L;
        request.order.store_id = 17L;
        request.order.order_type = "dine_in";
        request.order.table_no = "T1";
        request.order.table_display_name = "T1";
        request.order.submitted_at = LocalDateTime.of(2026, 11, 3, 12, 34);
        request.order.subtotal_amount = new BigDecimal("19.99");
        request.order.total_amount = new BigDecimal("22.98");
        request.store = new Store();
        request.store.name = "Live restaurant name";
        request.order_items = List.of(item(10L, "19.99"));
        request.order_item_options = List.of();
        request.kitchen_tasks = List.of(task(1L, 10L, "BACK_KITCHEN"));
        request.receipt_context = new ReceiptPresentation();
        request.receipt_context.language = "en";
        request.receipt_context.restaurant_name = "Frozen restaurant";
        request.receipt_context.settings_revision = 7L;
        request.receipt_context.kitchen_station_codes = List.of("BACK_KITCHEN");
        request.receipt_context.order_item_ids = List.of(10L);
        request.receipt_context.item_names = Map.of("10", "Frozen meal");
        request.receipt_context.subtotal = new BigDecimal("19.99");
        request.receipt_context.total = new BigDecimal("22.98");
        return request;
    }

    private OrderItem item(long id, String lineAmount) {
        OrderItem item = new OrderItem();
        item.id = id;
        item.item_name_snapshot_zh = "Original snapshot";
        item.item_name_snapshot_en = "Original English snapshot";
        item.item_name_snapshot_fr = "Ancien nom";
        item.item_sku_snapshot = "SYNTHETIC_" + id;
        item.category_code_snapshot = "RICE";
        item.quantity = 1;
        item.unit_price = new BigDecimal("19.99");
        item.line_amount = new BigDecimal(lineAmount);
        return item;
    }

    private KitchenTask task(long id, long itemId, String station) {
        KitchenTask task = new KitchenTask();
        task.id = id;
        task.order_item_id = itemId;
        task.station_code = station;
        task.item_name_snapshot_zh = "LEGACY_TASK_NAME";
        task.quantity = 1;
        task.created_at = LocalDateTime.of(2026, 11, 3, 12, 34);
        task.status = "pending";
        return task;
    }

    private OrderItemOption option(long id, String group, String price, int quantity) {
        OrderItemOption option = new OrderItemOption();
        option.id = id;
        option.order_item_id = 10L;
        option.option_id = 1000L + id;
        option.option_group_snapshot = group;
        option.option_type_snapshot = "REMOVE".equals(group) ? "remove" : "addon";
        option.option_code_snapshot = "synthetic_" + id;
        option.option_name_snapshot_zh = "Original option " + id;
        option.option_name_snapshot_en = "Original option " + id;
        option.price_delta = new BigDecimal(price);
        option.quantity = quantity;
        return option;
    }

    private String sized(String line) {
        return "[[DOUBLE_HEIGHT]]" + line + "[[/DOUBLE_HEIGHT]]\n";
    }

    private String plain(String value) {
        return value.replaceAll("\\[\\[/?[^]]+]]", "");
    }

    private int occurrences(String value, String needle) {
        int count = 0;
        for (int position = value.indexOf(needle); position >= 0; position = value.indexOf(needle, position + needle.length())) count++;
        return count;
    }
}
