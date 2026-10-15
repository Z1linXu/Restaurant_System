package com.restaurant.system.printing.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import com.restaurant.system.printing.semantic.KitchenModifierTokenResolver;
import com.restaurant.system.printing.semantic.OptionSemanticResolver;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class NeutralProfilePrintingTest {
    @Test
    void neutralDictionaryDoesNotResolvePrivateAliasesOrIngredientAbbreviations() {
        var rules = PrintingDisplayRuleContext.neutralContext();
        assertThat(rules.lanzhouCompatibility()).isFalse();
        assertThat(rules.resolveItemAlias("GRAB", "beef_chow_mein", "Customer Full Name")).isEqualTo("Customer Full Name");
        assertThat(KitchenModifierTokenResolver.resolveAddon("ADD_ON", "tea_egg", "Customer Egg Label", null, 2, rules))
            .isEqualTo("Customer Egg Labelx2");
        assertThat(KitchenModifierTokenResolver.resolveRemove("remove_cilantro", "Customer Remove Label", null, rules))
            .isEqualTo("Customer Remove Label");
        assertThat(PrintingDisplayRuleContext.defaultContext().lanzhouCompatibility()).isTrue();
        assertThat(PrintingDisplayRuleContext.defaultContext().resolveItemAlias("GRAB", "beef_chow_mein", "fallback"))
            .isEqualTo("牛炒");
    }

    @Test
    void neutralKitchenRenderersUseFullSnapshotsDespiteCollidingLegacySkuAndCategory() {
        var request = fixture();
        assertThat(new GrabReceiptRenderer().render(request)).contains("Customer Full Name", "Customer Full Option")
            .doesNotContain("牛炒", "+蛋", "+香");
        var hot = new HotKitchenReceiptRenderer(new HotKitchenPrintEligibilityService(mock(MenuItemRepository.class), mock(OptionSemanticResolver.class)));
        assertThat(hot.render(request)).contains("Customer Full Name", "Customer Full Option").doesNotContain("牛炒", "+蛋");
    }

    @Test
    void neutralReceiptDoesNotHideZeroPriceComboChildrenOrInferNoodleNames() {
        var request = fixture();
        request.order_items.get(0).combo_role = "combo_egg";
        request.order_items.get(0).line_amount = BigDecimal.ZERO;
        assertThat(new FrontdeskReceiptRenderer().render(request)).contains("Customer Full Name", "Customer Full Option", "Total: $11.50")
            .doesNotContain("牛炒", "小碗", "小菜:");
    }

    @Test
    void neutralDoesNotInferHotRoutingFromPrivateSkuCategoryOrEgg() {
        var request = fixture();
        request.kitchen_tasks.get(0).station_code = "COLD";
        var eligibility = new HotKitchenPrintEligibilityService(mock(MenuItemRepository.class), mock(OptionSemanticResolver.class));
        assertThat(eligibility.resolveHotKitchenTasks(request)).isEmpty();
    }

    @Test
    void explicitStoreAliasStillWorksWithoutTurningOnPrivateFallbacks() {
        var request = fixture();
        var content = request.printing_rules.content().deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) content.path("item_aliases")).addObject()
            .put("item_sku", "beef_chow_mein").putObject("outputs").put("GRAB", "Own Store Alias");
        request.printing_rules = new PrintingDisplayRuleContext(null, 1, "own", content, false);
        assertThat(new GrabReceiptRenderer().render(request)).contains("Own Store Alias", "Customer Full Option").doesNotContain("牛炒");
    }

    private PrintRenderRequest fixture() {
        var request = new PrintRenderRequest();
        request.printing_rules = PrintingDisplayRuleContext.neutralContext();
        request.order = new Order();
        request.order.id = 1L;
        request.order.order_no = "SYNTHETIC";
        request.order.pickup_no = "N1";
        request.order.order_type = "pickup";
        request.order.subtotal_amount = new BigDecimal("10.00");
        request.order.total_amount = new BigDecimal("11.50");
        var item = new OrderItem();
        item.id = 2L; item.order_id = 1L; item.quantity = 1;
        item.item_sku_snapshot = "beef_chow_mein";
        item.category_code_snapshot = "FRIED_NOODLE";
        item.item_name_snapshot_zh = "Customer Full Name";
        item.item_name_snapshot_en = "Customer Full Name";
        item.unit_price = new BigDecimal("10.00"); item.line_amount = item.unit_price;
        request.order_items = List.of(item);
        var option = new OrderItemOption();
        option.order_item_id = 2L; option.option_code_snapshot = "tea_egg";
        option.option_name_snapshot_zh = "Customer Full Option";
        option.option_type_snapshot = "addon"; option.quantity = 1;
        request.order_item_options = List.of(option);
        var task = new KitchenTask();
        task.id = 3L; task.order_item_id = 2L; task.quantity = 1; task.station_code = "WOK";
        task.item_name_snapshot_zh = item.item_name_snapshot_zh;
        task.special_instructions_snapshot = "Customer Full Option";
        request.kitchen_tasks = List.of(task);
        return request;
    }
}
