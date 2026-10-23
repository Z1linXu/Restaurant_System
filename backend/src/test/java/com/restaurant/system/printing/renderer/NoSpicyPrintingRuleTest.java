package com.restaurant.system.printing.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.rules.PrintingAddonVocabulary;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import com.restaurant.system.printing.rules.PrintingDisplayRuleRevisionRepository;
import com.restaurant.system.printing.rules.PrintingDisplayRuleServiceImpl;
import com.restaurant.system.printing.rules.PrintingDisplayRuleSetRepository;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRulePreviewRequest;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoSpicyPrintingRuleTest {

    @Test
    void hotKitchenUsesItsOwnNoSpicyRuleForSubmitAndUpdateWithoutChangingFrozenTask() {
        for (boolean update : new boolean[] {false, true}) {
            PrintRenderRequest request = request(customRules("（走G）", "（走H）"));
            request.is_update_ticket = update;
            HotKitchenPrintEligibilityService eligibility = mock(HotKitchenPrintEligibilityService.class);
            when(eligibility.resolveHotKitchenTasks(request)).thenReturn(request.kitchen_tasks);

            String text = new HotKitchenReceiptRenderer(eligibility).render(request);

            assertThat(text).contains("大红（走H）×1").doesNotContain("（走G）");
            assertThat(request.kitchen_tasks.get(0).special_instructions_snapshot).isEqualTo("大红");
            assertThat(text.contains(" (UPDATE)")).isEqualTo(update);
            assertThat(text).doesNotContain("UPDATED");
        }
    }

    @Test
    void codeOnlyFrozenSpicinessUsesThePublishedHotKitchenOutput() {
        PrintRenderRequest request = request(customRules("（走G）", "（走H）"));
        request.order_item_options.get(1).option_name_snapshot_zh = null;
        HotKitchenPrintEligibilityService eligibility = mock(HotKitchenPrintEligibilityService.class);
        when(eligibility.resolveHotKitchenTasks(request)).thenReturn(request.kitchen_tasks);

        assertThat(new HotKitchenReceiptRenderer(eligibility).render(request)).contains("大红（走H）×1");
    }

    @Test
    void defaultStoreStillOmitsNoSpicyOnHotKitchen() {
        PrintRenderRequest request = request(PrintingDisplayRuleContext.defaultContext());
        HotKitchenPrintEligibilityService eligibility = mock(HotKitchenPrintEligibilityService.class);
        when(eligibility.resolveHotKitchenTasks(request)).thenReturn(request.kitchen_tasks);

        assertThat(new HotKitchenReceiptRenderer(eligibility).render(request)).contains("大红×1")
            .doesNotContain("走s", "不辣");
    }

    @Test
    void previewUsesTheSameKitchenRulesAndKeepsCustomerReceiptWording() {
        StoreRepository stores = mock(StoreRepository.class);
        when(stores.existsById(2L)).thenReturn(true);
        PrintingDisplayRuleServiceImpl service = new PrintingDisplayRuleServiceImpl(
            mock(PrintingDisplayRuleSetRepository.class), mock(PrintingDisplayRuleRevisionRepository.class),
            stores, mock(PrintJobRepository.class), mock(PrintingAddonVocabulary.class), mock(jakarta.persistence.EntityManager.class)
        );
        PrintingDisplayRulePreviewRequest request = new PrintingDisplayRulePreviewRequest();
        request.store_id = 2L;
        request.content = customRules("（走G）", "（走H）").content();
        request.spiciness_zh = "不辣";
        request.modifier_add_codes = List.of();
        request.modifier_remove_codes = List.of();

        var preview = service.preview(request);

        assertThat(preview.grab_preview).contains("（走G）").doesNotContain("（走H）");
        assertThat(preview.hot_kitchen_preview).contains("（走H）").doesNotContain("（走G）");
        assertThat(preview.frontdesk_receipt_preview).contains("辣度: 不辣").doesNotContain("走G", "走H");
    }

    private PrintRenderRequest request(PrintingDisplayRuleContext rules) {
        Order order = new Order();
        order.id = 1L;
        order.order_type = "dine_in";
        order.table_no = "T1";
        OrderItem item = new OrderItem();
        item.id = 10L;
        item.item_sku_snapshot = "braised_beef_tendon_noodle";
        item.item_name_snapshot_zh = "红烧牛腱面";
        item.category_code_snapshot = "SOUP_NOODLE";
        item.quantity = 1;
        KitchenTask task = new KitchenTask();
        task.id = 20L;
        task.order_item_id = item.id;
        task.station_code = "NOODLE";
        task.item_name_snapshot_zh = "红";
        task.special_instructions_snapshot = "大红";
        task.quantity = 1;
        task.status = "pending";
        OrderItemOption size = new OrderItemOption();
        size.order_item_id = item.id;
        size.option_type_snapshot = "size";
        size.option_code_snapshot = "size_large";
        size.option_name_snapshot_zh = "大碗";
        OrderItemOption spicy = new OrderItemOption();
        spicy.order_item_id = item.id;
        spicy.option_type_snapshot = "spicy_level";
        spicy.option_group_snapshot = "SPICY_LEVEL";
        spicy.option_code_snapshot = "spicy_none";
        spicy.option_name_snapshot_zh = "不辣";
        PrintRenderRequest request = new PrintRenderRequest();
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = List.of(size, spicy);
        request.kitchen_tasks = List.of(task);
        request.printing_rules = rules;
        return request;
    }

    private PrintingDisplayRuleContext customRules(String grab, String hot) {
        PrintingDisplayRuleContext rules = PrintingDisplayRuleContext.defaultContext();
        ObjectNode outputs = (ObjectNode) rules.content().path("dictionaries").path("SPICINESS").get(0).path("outputs");
        outputs.put("GRAB", grab);
        outputs.put("HOT_KITCHEN", hot);
        return rules;
    }
}
