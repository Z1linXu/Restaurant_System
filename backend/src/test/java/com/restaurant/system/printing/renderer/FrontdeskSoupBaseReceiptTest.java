package com.restaurant.system.printing.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrontdeskSoupBaseReceiptTest {

    private final FrontdeskReceiptRenderer renderer = new FrontdeskReceiptRenderer();

    @Test
    void zeroPriceSoupBaseIsExplicitOnFullAndUpdateReceipts() {
        for (boolean update : new boolean[] {false, true}) {
            for (String label : new String[] {"素汤", "肉汤"}) {
                PrintRenderRequest request = request(soup("SOUP_BASE", "soup_base",
                    "素汤".equals(label) ? "soup_vegan" : "soup_beef", label));
                request.is_update_ticket = update;

                String text = renderer.render(request);

                assertThat(text).contains("大碗蔬菜面", "汤底：" + label);
                assertThat(text.contains(" (UPDATE)")).isEqualTo(update);
                assertThat(text).doesNotContain("UPDATED");
                assertThat(text).contains("Subtotal: $14.99", "Tax (14.975%): $2.24", "Total: $17.23");
            }
        }
    }

    @Test
    void snapshotGroupIdentifiesRenamedCustomSoupWithoutReadingCurrentMenu() {
        PrintRenderRequest request = request(soup("SOUP_BASE", null, "store_private_broth", "当时选择的清汤"));

        assertThat(renderer.render(request)).contains("汤底：当时选择的清汤");
    }

    @Test
    void legacyTypeOrKnownStableCodeStillIdentifiesSoup() {
        for (OrderItemOption option : List.of(
            soup(null, "soup_base", null, "旧素汤名称"),
            soup(null, null, "soup_beef", "旧肉汤名称")
        )) {
            assertThat(renderer.render(request(option)))
                .contains("汤底：" + option.option_name_snapshot_zh);
        }
    }

    @Test
    void ordinaryBeefNoodleDoesNotGainAnAssumedSoupBase() {
        PrintRenderRequest request = request();
        request.order_items.get(0).item_sku_snapshot = "traditional_beef_noodle";
        request.order_items.get(0).item_name_snapshot_zh = "牛肉面";

        assertThat(renderer.render(request)).doesNotContain("汤底");
    }

    @Test
    void explicitOtherGroupIsNotReclassifiedByACollidingLegacyCode() {
        PrintRenderRequest request = request(soup("ADD_ON", "addon", "soup_beef", "另店的加料"));
        request.order_items.get(0).item_sku_snapshot = "other_store_dish";

        assertThat(renderer.render(request)).doesNotContain("汤底");
    }

    @Test
    void vegetableOrderWithoutSoupSnapshotDoesNotInventVegetarianBroth() {
        assertThat(renderer.render(request()))
            .contains("汤底：未记录").doesNotContain("汤底：素汤", "汤底：肉汤");
    }

    @Test
    void paidSoupBaseIsNotRepeatedAsAnAddonAndAmountsRemainFrozen() {
        OrderItemOption broth = soup("SOUP_BASE", "soup_base", "soup_beef", "肉汤");
        broth.price_delta = new BigDecimal("1.00");
        PrintRenderRequest request = request(broth);

        String text = renderer.render(request);

        assertThat(text.split("肉汤", -1)).hasSize(2);
        assertThat(text).contains("Subtotal: $14.99", "Total: $17.23");
    }

    @Test
    void eachPrintedBowlKeepsItsFrozenSoupSelection() {
        PrintRenderRequest request = request(soup("SOUP_BASE", "soup_base", "soup_beef", "肉汤"));
        request.order_items.get(0).quantity = 2;
        request.order_items.get(0).line_amount = new BigDecimal("29.98");
        request.order.subtotal_amount = new BigDecimal("29.98");
        request.order.total_amount = new BigDecimal("34.47");

        assertThat(renderer.render(request).split("汤底：肉汤", -1)).hasSize(3);
    }

    @Test
    void frontdeskUsesItsOwnSoupWordingWithoutKitchenShorthand() {
        PrintRenderRequest request = request(soup("SOUP_BASE", "soup_base", "soup_vegan", "素汤"));
        request.printing_rules = PrintingDisplayRuleContext.defaultContext();
        ObjectNode outputs = (ObjectNode) request.printing_rules.content().path("dictionaries").path("SOUP_BASE").get(0).path("outputs");
        outputs.put("GRAB", "厨房G");
        outputs.put("HOT_KITCHEN", "厨房H");
        outputs.put("FRONTDESK_RECEIPT", "素汤（顾客）");

        assertThat(renderer.render(request)).contains("汤底：素汤（顾客）").doesNotContain("厨房G", "厨房H");
    }

    private PrintRenderRequest request(OrderItemOption... soupOptions) {
        Order order = new Order();
        order.id = 1L;
        order.order_type = "dine_in";
        order.table_no = "T1";
        order.subtotal_amount = new BigDecimal("14.99");
        order.total_amount = new BigDecimal("17.23");
        OrderItem item = new OrderItem();
        item.id = 10L;
        item.item_sku_snapshot = "vegetable_noodle";
        item.item_name_snapshot_zh = "蔬菜面";
        item.category_code_snapshot = "SOUP_NOODLE";
        item.quantity = 1;
        item.unit_price = new BigDecimal("14.99");
        item.line_amount = new BigDecimal("14.99");
        item.status = "submitted";
        OrderItemOption size = new OrderItemOption();
        size.order_item_id = item.id;
        size.option_type_snapshot = "size";
        size.option_code_snapshot = "size_large";
        size.option_name_snapshot_zh = "大碗";
        ArrayList<OrderItemOption> options = new ArrayList<>();
        options.add(size);
        options.addAll(List.of(soupOptions));
        PrintRenderRequest request = new PrintRenderRequest();
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = options;
        return request;
    }

    private OrderItemOption soup(String group, String type, String code, String label) {
        OrderItemOption option = new OrderItemOption();
        option.order_item_id = 10L;
        option.option_group_snapshot = group;
        option.option_type_snapshot = type;
        option.option_code_snapshot = code;
        option.option_name_snapshot_zh = label;
        option.price_delta = BigDecimal.ZERO;
        option.quantity = 1;
        return option;
    }
}
