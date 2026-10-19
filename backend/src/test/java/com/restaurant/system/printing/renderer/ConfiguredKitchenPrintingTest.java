package com.restaurant.system.printing.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import com.restaurant.system.printing.semantic.OptionSemanticResolver;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfiguredKitchenPrintingTest {
    @Test
    void oneSummaryUsesConfiguredStationAndTitleWithoutPrivateAbbreviations() {
        var request = fixture();
        String output = new GrabReceiptRenderer().render(request);
        assertThat(output).containsOnlyOnce("后厨").contains("3 x 经典牛肉面", "细面 / 免费加面x1", "不要香菜")
            .doesNotContain("GRAB", "牛炒", "大碗", "中碗", "Drink");
    }

    @Test
    void sharedParentComponentsUseTheirOwnFrozenNamesAndAlreadyMultipliedQuantities() {
        var request = fixture();
        var soup = task(4L, "虾云吞汤（每碗3颗）", 3);
        soup.component_option_id = 401L;
        var roll = task(5L, "素春卷（1条）", 3);
        roll.component_option_id = 402L;
        request.kitchen_tasks = List.of(request.kitchen_tasks.get(0), soup, roll);
        String output = new GrabReceiptRenderer().render(request);
        assertThat(output).containsOnlyOnce("3 x 经典牛肉面")
            .containsOnlyOnce("3 x 虾云吞汤（每碗3颗）")
            .containsOnlyOnce("3 x 素春卷（1条）")
            .containsOnlyOnce("不要香菜")
            .doesNotContain("9 x", "6 x");
    }

    @Test
    void noodleAppetizerKeepsItsTwoPieceServingAndDoesNotInheritParentAlias() {
        var request = fixture();
        var content = (ObjectNode) request.printing_rules.content().deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) content.path("item_aliases")).addObject()
            .put("item_sku", "beef_chow_mein").putObject("outputs").put("GRAB", "本店主菜名称");
        request.printing_rules = new PrintingDisplayRuleContext(1L, 1, "configured", content, false);
        var roll = task(5L, "炸素春卷（2条/份）", 3);
        roll.component_option_id = 402L;
        request.kitchen_tasks = List.of(request.kitchen_tasks.get(0), roll);
        assertThat(new GrabReceiptRenderer().render(request)).containsOnlyOnce("本店主菜名称")
            .contains("3 x 炸素春卷（2条/份）");
    }

    @Test
    void cancelledAndUnselectedStationsAreExcludedWithoutASecondHotTicket() {
        var request = fixture();
        var cancelled = task(4L, "已取消商品", 1);
        cancelled.status = "cancelled";
        var other = task(5L, "其他工位商品", 1);
        other.station_code = "BAR";
        request.kitchen_tasks = List.of(request.kitchen_tasks.get(0), cancelled, other);
        assertThat(new GrabReceiptRenderer().render(request)).doesNotContain("已取消商品", "其他工位商品");
        var hot = new HotKitchenReceiptRenderer(new HotKitchenPrintEligibilityService(
            mock(MenuItemRepository.class), mock(OptionSemanticResolver.class)));
        assertThat(hot.render(request)).isEmpty();
    }

    @Test
    void unconfiguredNeutralAndExplicitLegacyRoutingRemainUnchanged() {
        var request = fixture();
        request.printing_rules = PrintingDisplayRuleContext.neutralContext();
        assertThat(new GrabReceiptRenderer().render(request)).isEmpty();
        var configured = rules();
        request.printing_rules = new PrintingDisplayRuleContext(null, 1, "legacy", configured.content(), true);
        assertThat(new GrabReceiptRenderer().render(request)).isEmpty();
    }

    @Test
    void chineseReceiptPreservesFrozenAmountsAndShowsZeroPriceSelections() {
        var request = fixture();
        String output = new FrontdeskReceiptRenderer().render(request);
        assertThat(output).contains("3 x 经典牛肉面", "细面 x1", "免费加面 x1", "牛肉 x1", "虾 x1",
            "小计：$59.97", "税额 (14.975%): $8.98", "合计：$68.95", "下单时间：")
            .doesNotContain("Subtotal", "Total:", "走蛋", "Regular", "Small");
    }

    @Test
    void updateSummaryRemainsOneChineseAddedItemsTicket() {
        var request = fixture();
        request.is_update_ticket = true;
        assertThat(new GrabReceiptRenderer().render(request)).containsOnlyOnce("后厨").contains("追加单");
        assertThat(new FrontdeskReceiptRenderer().render(request)).contains("追加单", "仅新增商品", "小计：$59.97");
    }

    private PrintingDisplayRuleContext rules() {
        var content = (ObjectNode) PrintingDisplayRuleContext.neutralContext().content().deepCopy();
        content.with("formatting").put("grab_station_codes", "BACK_KITCHEN")
            .put("grab_title", "后厨").put("receipt_language", "zh");
        return new PrintingDisplayRuleContext(1L, 1, "configured", content, false);
    }

    private PrintRenderRequest fixture() {
        var request = new PrintRenderRequest();
        request.printing_rules = rules();
        request.order = new Order();
        request.order.id = 1L;
        request.order.order_no = "SYNTHETIC";
        request.order.table_no = "T1";
        request.order.order_type = "dine_in";
        request.order.subtotal_amount = new BigDecimal("59.97");
        request.order.total_amount = new BigDecimal("68.95");
        request.order.submitted_at = LocalDateTime.of(2026, 10, 18, 12, 0);
        var item = new OrderItem();
        item.id = 2L;
        item.order_id = 1L;
        item.quantity = 3;
        item.item_sku_snapshot = "beef_chow_mein"; // A colliding legacy SKU cannot select private presentation.
        item.category_code_snapshot = "SOUP_NOODLE";
        item.item_name_snapshot_zh = "经典牛肉面";
        item.item_name_snapshot_en = "Classic beef noodle soup";
        item.notes = "不要香菜";
        item.unit_price = new BigDecimal("19.99");
        item.line_amount = new BigDecimal("59.97");
        request.order_items = List.of(item);
        request.order_item_options = List.of(option("细面"), option("免费加面"), option("牛肉"), option("虾"));
        var main = task(3L, item.item_name_snapshot_zh, 3);
        main.special_instructions_snapshot = "细面 / 免费加面x1";
        request.kitchen_tasks = List.of(main);
        return request;
    }

    private KitchenTask task(Long id, String name, int quantity) {
        var task = new KitchenTask();
        task.id = id;
        task.order_item_id = 2L;
        task.station_code = "BACK_KITCHEN";
        task.item_name_snapshot_zh = name;
        task.quantity = quantity;
        task.status = "pending";
        return task;
    }

    private OrderItemOption option(String name) {
        var option = new OrderItemOption();
        option.order_item_id = 2L;
        option.option_name_snapshot_zh = name;
        option.quantity = 1;
        option.price_delta = BigDecimal.ZERO;
        return option;
    }
}
