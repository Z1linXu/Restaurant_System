package com.restaurant.system.printing.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DineInTicketHeaderTest {
    private static final String DIVIDER = "--------------------------------";

    @ParameterizedTest(name = "{0} update={1} table={2}")
    @MethodSource("tableHeaders")
    void allSixTicketTypesStartWithCompleteTableCodeInOriginalLargeMarkup(
        String module, boolean update, String tableCode, String displayCode
    ) {
        for (Long storeId : List.of(1L, 2L, 3L, 97L)) {
            var request = request(module, update);
            request.order.store_id = storeId;
            request.order.table_no = tableCode;
            String before = request.order.table_no;

            String encoded = render(module, request);
            assertThat(encoded).startsWith(PrintMarkup.TOP_FEED_16MM);
            String raw = PrintMarkup.stripTopFeed(encoded);
            String expected = displayCode + (update ? " (UPDATE)" : "");

            assertThat(raw.lines().findFirst()).contains(PrintMarkup.large(displayCode) + (update ? " (UPDATE)" : ""));
            assertThat(plain(raw).lines().limit(2).toList()).containsExactly(expected, DIVIDER);
            assertThat(raw).doesNotContain("桌号:", "桌号：", "UPDATED", "Added items only");
            assertThat(raw).contains("炒饭");
            assertThat(request.order.table_no).isEqualTo(before);
            if (PrintModuleCode.HOT_KITCHEN.equals(module)) {
                assertThat(raw.lines().skip(2).findFirst()).contains(PrintMarkup.large("HOT KITCHEN"));
            }
        }
    }

    @ParameterizedTest(name = "{0} update={1}")
    @MethodSource("ticketTypes")
    void missingDineInTableRetainsWalkInFallback(String module, boolean update) {
        for (String tableCode : new String[] {null, "", "  "}) {
            var request = request(module, update);
            request.order.table_no = tableCode;
            assertThat(PrintMarkup.stripTopFeed(render(module, request))).startsWith(PrintMarkup.large("Walk-in") + (update ? " (UPDATE)" : "") + "\n");
        }
    }

    @ParameterizedTest(name = "{0} update={1}")
    @MethodSource("ticketTypes")
    void takeoutAndPickupKeepTheirExistingHeadersAndUpdateMarkers(String module, boolean update) {
        for (String orderType : List.of("takeout", "pickup")) {
            var request = request(module, update);
            request.order.order_type = orderType;
            request.order.table_no = null;
            request.order.pickup_no = "TO-18";
            String raw = render(module, request);
            String expected;
            if (PrintModuleCode.FRONTDESK_RECEIPT.equals(module)) {
                expected = PrintMarkup.large("取餐号: TO-18") + "\n"
                    + (update ? PrintMarkup.large("UPDATED") + "\n" + PrintMarkup.doubleHeight("Added items only") + "\n" : "")
                    + PrintMarkup.doubleHeight("Order Type: Takeout") + "\n" + PrintMarkup.doubleHeight(DIVIDER) + "\n";
            } else {
                expected = (PrintModuleCode.GRAB.equals(module) ? "\n\n\n\n" : "\n\n\n")
                    + (update ? PrintMarkup.large("UPDATED") + "\n" + DIVIDER + "\n" : "")
                    + PrintMarkup.large("TO-18") + "\n"
                    + (PrintModuleCode.HOT_KITCHEN.equals(module) ? PrintMarkup.large("HOT KITCHEN") + "\n" : "")
                    + DIVIDER + "\n";
                assertThat(raw).contains(PrintMarkup.large(PrintModuleCode.GRAB.equals(module) ? "外卖" : "外卖 / TAKEOUT"));
            }
            assertThat(raw).startsWith(expected).doesNotContain(" (UPDATE)");
            assertThat(raw.contains(PrintMarkup.large("UPDATED"))).isEqualTo(update);
        }
    }

    @ParameterizedTest(name = "{0} update={1}")
    @MethodSource("ticketTypes")
    void externalKitchenMirrorRetainsItsExternalHeaderEvenForLegacyDineInType(String module, boolean update) {
        var request = request(module, update);
        request.order.external_source = "UBER_EATS";
        request.order.financial_mode = "EXTERNAL_PLATFORM";
        request.order.external_display_id = "AB12";
        request.order.external_customer_display_name = "Alice";
        assertThat(render(module, request)).startsWith(PrintMarkup.large("UBER - Alice") + "\n")
            .doesNotContain(PrintMarkup.large("1里"), " (UPDATE)");
    }

    private static Stream<Arguments> ticketTypes() {
        return Stream.of(PrintModuleCode.GRAB, PrintModuleCode.FRONTDESK_RECEIPT, PrintModuleCode.HOT_KITCHEN)
            .flatMap(module -> Stream.of(false, true).map(update -> Arguments.of(module, update)));
    }

    private static Stream<Arguments> tableHeaders() {
        return Stream.of(PrintModuleCode.GRAB, PrintModuleCode.FRONTDESK_RECEIPT, PrintModuleCode.HOT_KITCHEN)
            .flatMap(module -> Stream.of(false, true).flatMap(update -> Stream.of(
                new String[] {"1", "1"}, new String[] {"1里", "1里"}, new String[] {"1-左", "1-左"},
                new String[] {"1-右", "1-右"}, new String[] {"T1-A", "T1-左"}, new String[] {"T1-B", "T1-右"},
                new String[] {"12里-右", "12里-右"}
            ).map(code -> Arguments.of(module, update, code[0], code[1]))));
    }

    private PrintRenderRequest request(String module, boolean update) {
        var order = new Order();
        order.id = 1L;
        order.store_id = 1L;
        order.order_type = "dine_in";
        order.table_no = "1里";
        order.submitted_at = LocalDateTime.of(2026, 10, 23, 12, 30);
        order.subtotal_amount = new BigDecimal("12.00");
        order.total_amount = new BigDecimal("13.80");
        var item = new OrderItem();
        item.id = 10L;
        item.order_id = order.id;
        item.item_name_snapshot_zh = "炒饭";
        item.item_name_snapshot_en = "Fried Rice";
        item.category_code_snapshot = "RICE";
        item.quantity = 1;
        item.status = "submitted";
        item.unit_price = new BigDecimal("12.00");
        item.line_amount = new BigDecimal("12.00");
        var task = new KitchenTask();
        task.id = 20L;
        task.order_id = order.id;
        task.order_item_id = item.id;
        task.station_code = "WOK";
        task.item_name_snapshot_zh = "炒饭";
        task.quantity = 1;
        task.status = "pending";
        task.created_at = order.submitted_at;
        var request = new PrintRenderRequest();
        request.module_code = module;
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = List.of();
        request.kitchen_tasks = List.of(task);
        request.is_update_ticket = update;
        request.happened_at = order.submitted_at;
        return request;
    }

    private String render(String module, PrintRenderRequest request) {
        return switch (module) {
            case PrintModuleCode.GRAB -> new GrabReceiptRenderer().render(request);
            case PrintModuleCode.FRONTDESK_RECEIPT -> new FrontdeskReceiptRenderer().render(request);
            case PrintModuleCode.HOT_KITCHEN -> {
                var eligibility = mock(HotKitchenPrintEligibilityService.class);
                when(eligibility.resolveHotKitchenTasks(request)).thenReturn(request.kitchen_tasks);
                yield new HotKitchenReceiptRenderer(eligibility).render(request);
            }
            default -> throw new IllegalArgumentException(module);
        };
    }

    private String plain(String value) {
        return value.replaceAll("\\[\\[/?(?:DOUBLE_HEIGHT|LARGE|SMALL)]]", "");
    }
}
