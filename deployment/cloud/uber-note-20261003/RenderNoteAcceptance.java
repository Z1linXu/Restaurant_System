import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.renderer.GrabReceiptRenderer;
import com.restaurant.system.printing.renderer.HotKitchenReceiptRenderer;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import com.restaurant.system.printing.semantic.OptionSemanticResolver;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bounded read-only rendering proof. stdin is mapping-preview response.request, not a real order.
 * Compile against the reviewed application classes; execute against the exact deployed JAR classes.
 * No Spring context, repositories, database, network, dispatch or printer is initialized.
 * Synthetic WOK tasks prove note rendering only, not routing or durable submission/reprint.
 */
public final class RenderNoteAcceptance {
    private static final String CART_NOTE = "Please make everything less salty";
    private static final String FIRST_NOTE = "No cilantro";
    private static final String SECOND_NOTE = "Extra soup";
    private static final String RAW_NOTE = "Raw item customer note";
    private static final Set<String> CHOW_SKUS = Set.of(
            "beef_chow_mein", "chicken_chow_mein", "tomato_chow_mein", "vegetable_chow_mein");

    public static void main(String[] args) throws Exception {
        ObjectMapper json = new ObjectMapper();
        try {
            require(args.length == 0, "NO_ARGUMENTS_EXPECTED");
            byte[] input = System.in.readNBytes(262145);
            require(input.length <= 262144, "FIXTURE_TOO_LARGE");
            CreateOrderRequest source = json.readValue(input, CreateOrderRequest.class);
            PrintRenderRequest request = buildSyntheticRequest(source);
            request.module_code = PrintModuleCode.GRAB;
            String grab = new GrabReceiptRenderer().render(request);
            // Every mapped fixture item has an explicit confirmed chow-mein SKU and synthetic WOK task.
            // Raw roots return false before repository lookups. Null dependencies cannot access data.
            request.module_code = PrintModuleCode.HOT_KITCHEN;
            String hot = new HotKitchenReceiptRenderer(new HotKitchenPrintEligibilityService(
                    null, new OptionSemanticResolver(null))).render(request);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "PASS");
            result.put("acceptance_kind", "SYNTHETIC_PREVIEW_RENDER_ONLY");
            result.put("task_routing", "SYNTHETIC_WOK_FOR_CONFIRMED_CHOW_MEIN_AND_RAW_HOLDING");
            result.put("durable_order_created", false);
            result.put("database_written", false);
            result.put("print_dispatched", false);
            result.put("real_uber_e2e", false);
            result.put("fixture_roots", 10);
            result.put("mapped_roots", 9);
            result.put("raw_roots", 1);
            result.put("GRAB", verify(grab, true));
            result.put("HOT_KITCHEN", verify(hot, false));
            System.out.println(json.writeValueAsString(result));
        } catch (Exception failure) {
            // Do not echo input JSON or parser messages: stdout is safe structured evidence only.
            String reason = failure instanceof IllegalStateException && failure.getMessage() != null
                    && failure.getMessage().matches("[A-Z_]+") ? failure.getMessage() : "FIXTURE_OR_RENDER_FAILED";
            System.out.println(json.writeValueAsString(Map.of("status", "FAIL", "reason", reason)));
            System.exit(1);
        }
    }

    private static PrintRenderRequest buildSyntheticRequest(CreateOrderRequest source) {
        require(source != null && Long.valueOf(1L).equals(source.store_id)
                && "delivery".equals(source.order_type) && source.table_no == null && source.pickup_no == null,
                "FIXTURE_SCOPE_INVALID");
        require(CART_NOTE.equals(source.external_order_note_snapshot), "CART_NOTE_NOT_FROZEN");
        require(source.items != null && source.items.size() == 10, "TEN_ROOT_FIXTURE_REQUIRED");

        Order order = new Order();
        order.id = -1L;
        order.store_id = source.store_id;
        order.order_type = "delivery";
        order.financial_mode = "EXTERNAL_PLATFORM";
        order.external_source = "UBER_EATS";
        order.external_display_id = "SYNTHETIC";
        order.external_customer_display_name = "Synthetic Fixture";
        order.external_order_note_snapshot = source.external_order_note_snapshot;
        order.submitted_at = LocalDateTime.of(2026, 10, 3, 12, 0);
        PrintRenderRequest request = new PrintRenderRequest();
        request.order = order;
        request.order_items = new ArrayList<>();
        request.order_item_options = new ArrayList<>();
        request.kitchen_tasks = new ArrayList<>();
        request.printing_rules = PrintingDisplayRuleContext.defaultContext();
        request.happened_at = order.submitted_at;
        int rawCount = 0;
        int firstNotes = 0;
        int secondNotes = 0;
        for (CreateOrderItemRequest line : source.items) {
            require(line != null && Integer.valueOf(1).equals(line.quantity), "UNIT_QUANTITY_REQUIRED");
            boolean raw = line.external_kitchen_snapshot != null && line.external_kitchen_snapshot.rawRoot();
            if (raw) {
                rawCount++;
                require(line.menu_item_id == null && line.station_id_snapshot == null
                        && RAW_NOTE.equals(line.external_kitchen_snapshot.notes())
                        && blank(line.notes) && line.external_kitchen_snapshot.modifiers().isEmpty(),
                        "RAW_FIXTURE_INVALID");
            } else {
                require(line.external_kitchen_snapshot == null && line.menu_item_id != null
                        && line.station_id_snapshot != null && CHOW_SKUS.contains(line.item_sku_snapshot),
                        "ONLY_MAPPED_CHOW_MEIN_SUPPORTED");
                require(blank(line.notes) || FIRST_NOTE.equals(line.notes) || SECOND_NOTE.equals(line.notes),
                        "ITEM_NOTE_NOT_SCOPED");
                firstNotes += FIRST_NOTE.equals(line.notes) ? 1 : 0;
                secondNotes += SECOND_NOTE.equals(line.notes) ? 1 : 0;
            }
            OrderItem item = new OrderItem();
            item.id = -100L - request.order_items.size();
            item.order_id = order.id;
            item.menu_item_id = line.menu_item_id;
            item.station_id_snapshot = line.station_id_snapshot;
            item.category_code_snapshot = line.category_code_snapshot;
            item.item_sku_snapshot = line.item_sku_snapshot;
            item.item_name_snapshot_zh = line.item_name_snapshot_zh;
            item.item_name_snapshot_en = line.item_name_snapshot_en;
            item.quantity = line.quantity;
            item.notes = line.notes;
            item.externalKitchenSnapshot = line.external_kitchen_snapshot;
            item.combo_group_no = line.combo_group_no;
            item.combo_role = line.combo_role;
            item.unit_price = BigDecimal.ZERO;
            item.line_amount = BigDecimal.ZERO;
            request.order_items.add(item);
            require(line.options != null, "OPTIONS_REQUIRED");
            for (var choice : line.options) {
                OrderItemOption option = new OrderItemOption();
                option.id = -1000L - request.order_item_options.size();
                option.order_item_id = item.id;
                option.option_id = choice.option_id;
                option.option_type_snapshot = choice.option_type_snapshot;
                option.option_code_snapshot = choice.option_code_snapshot;
                option.option_group_snapshot = choice.option_group_snapshot;
                option.parent_option_id_snapshot = choice.parent_option_id_snapshot;
                option.option_name_snapshot_zh = choice.option_name_snapshot_zh;
                option.option_name_snapshot_en = choice.option_name_snapshot_en;
                option.quantity = choice.quantity;
                option.price_delta = BigDecimal.ZERO;
                request.order_item_options.add(option);
            }
            KitchenTask task = new KitchenTask();
            task.id = item.id;
            task.order_id = order.id;
            task.order_item_id = item.id;
            task.store_id = order.store_id;
            task.station_code = raw ? "RAW_UBER_FALLBACK" : "WOK";
            task.item_name_snapshot_zh = item.item_name_snapshot_zh;
            task.item_name_snapshot_en = item.item_name_snapshot_en;
            task.quantity = item.quantity;
            task.status = "pending";
            task.created_at = order.submitted_at.plusSeconds(request.kitchen_tasks.size());
            request.kitchen_tasks.add(task);
        }
        require(rawCount == 1 && firstNotes == 1 && secondNotes == 1, "MIXED_NOTE_FIXTURE_INVALID");
        return request;
    }

    private static Map<String, Object> verify(String rendered, boolean grab) {
        require(count(rendered, CART_NOTE) == 1 && count(rendered, "订单备注：") == 1, "ORDER_NOTE_COUNT_INVALID");
        require(count(rendered, FIRST_NOTE) == 1 && count(rendered, SECOND_NOTE) == 1, "ITEM_NOTES_NOT_PRESERVED");
        require(count(rendered, RAW_NOTE) == (grab ? 1 : 0), "RAW_NOTE_COUNT_INVALID");
        require(rendered.indexOf(FIRST_NOTE) < rendered.indexOf("订单备注：")
                && rendered.indexOf(SECOND_NOTE) < rendered.indexOf("订单备注：")
                && (!grab || rendered.indexOf(RAW_NOTE) < rendered.indexOf("订单备注："))
                && rendered.indexOf(CART_NOTE) < rendered.indexOf("外卖"), "NOTE_POSITION_INVALID");
        return Map.of("order_note_count", 1, "distinct_item_notes_preserved", true,
                "raw_item_note_count", grab ? 1 : 0, "order_note_before_takeout", true,
                "rendered_snapshot", rendered);
    }

    private static int count(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }
}
