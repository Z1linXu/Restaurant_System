import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.renderer.*;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import com.restaurant.system.printing.service.impl.PrintJobServiceImpl;
import com.restaurant.system.printing.transport.EscPosTcpPrinterTransport;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.*;

/** Render-only proof against the deployed JAR. No Spring, database, job or socket. */
public final class RenderHeaderAcceptance {
    private static final byte[] FEED = {0x1D, 0x50, 0, (byte)254, 0x1B, 0x4A, (byte)160, 0x1D, 0x50, 0, 0};
    public static void main(String[] args) throws Exception {
        var payloadMethod = PrintJobServiceImpl.class.getDeclaredMethod(
            "buildEscPosPayloadBase64", String.class, PrinterConfig.class, String.class);
        payloadMethod.setAccessible(true);
        var tcpMethod = EscPosTcpPrinterTransport.class.getDeclaredMethod("writeContent", OutputStream.class,
            String.class, Charset.class, String.class);
        tcpMethod.setAccessible(true);
        var payloadService = new PrintJobServiceImpl(null, null, null);
        var printer = new PrinterConfig();
        printer.text_encoding = "GBK";
        var renderers = List.of(new GrabReceiptRenderer(), new FrontdeskReceiptRenderer(),
            new HotKitchenReceiptRenderer(new HotKitchenPrintEligibilityService(null, null)));
        Map<String, Object> modules = new LinkedHashMap<>();
        for (var renderer : renderers) {
            for (boolean update : new boolean[] {false, true}) {
                List<String> verified = new ArrayList<>();
                for (String table : List.of("1", "1里", "1-左", "1-右")) {
                    var request = fixture(table, update);
                    request.module_code = renderer.getModuleCode();
                    String text = renderer.render(request);
                    require(text.startsWith(PrintMarkup.TOP_FEED_16MM));
                    String visible = PrintMarkup.stripTopFeed(text);
                    String expected = table + (update ? " (UPDATE)" : "");
                    require(visible.startsWith(PrintMarkup.large(table) + (update ? " (UPDATE)" : "") + "\n"));
                    require(visible.replaceAll("\\[\\[/?(?:DOUBLE_HEIGHT|LARGE|SMALL)]]", "")
                        .startsWith(expected + "\n--------------------------------\n"));
                    require(!text.contains("桌号：") && !text.contains("UPDATED"));
                    byte[] bytes = Base64.getDecoder().decode((String) payloadMethod.invoke(
                        payloadService, text, printer, "SMALL"));
                    var prefixBytes = new ByteArrayOutputStream();
                    prefixBytes.write(new byte[]{0x1B, 0x40}); prefixBytes.write(FEED);
                    prefixBytes.write(("\u001d!3" + table + "\u001d!\u0000" + (update ? " (UPDATE)" : "") + "\n")
                        .getBytes(Charset.forName("GBK")));
                    byte[] prefix = prefixBytes.toByteArray();
                    require(bytes.length > prefix.length && Arrays.equals(
                        prefix, Arrays.copyOf(bytes, prefix.length)));
                    require(occurrences(bytes, FEED) == 1 && occurrences(bytes, new byte[]{0x1B, 0x40}) == 1);
                    require(Arrays.equals(new byte[]{0x1D, 0x56, 0x41, 0x10}, Arrays.copyOfRange(bytes, bytes.length - 4, bytes.length)));
                    require(Arrays.equals(bytes, Base64.getDecoder().decode((String) payloadMethod.invoke(
                        payloadService, PrintMarkup.TOP_FEED_16MM + text, printer, "SMALL"))));
                    var tcp = new ByteArrayOutputStream(); tcp.write(new byte[]{0x1B, 0x40});
                    tcpMethod.invoke(new EscPosTcpPrinterTransport(null), tcp, text, Charset.forName("GBK"), "SMALL");
                    tcp.write(new byte[]{0x1D, 0x56, 0x41, 0x10});
                    require(Arrays.equals(bytes, tcp.toByteArray()));
                    // 80mm devices commonly offer 42/48 Font-A columns. Keep only the table 4x wide.
                    int columns = table.codePoints().map(c -> c >= 0x2e80 ? 2 : 1).sum() * 4 + (update ? 9 : 0);
                    require(columns <= 42);
                    verified.add(expected);
                }
                modules.put(renderer.getModuleCode() + (update ? "_UPDATE" : ""), verified);
                var takeout = fixture("1", update); takeout.order.order_type = "takeout";
                takeout.order.table_no = null; takeout.order.pickup_no = "TO-1";
                String takeoutText = renderer.render(takeout);
                require(!takeoutText.startsWith(PrintMarkup.TOP_FEED_16MM));
                require(occurrences(Base64.getDecoder().decode((String) payloadMethod.invoke(
                    payloadService, takeoutText, printer, "SMALL")), FEED) == 0);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(Map.of(
            "status", "PASS", "modules", modules, "header_cases", 24,
            "large_font_preserved", true, "leftmost_payload_prefix", true, "fits_42_columns", true,
            "database_written", false, "print_dispatched", false));
        result.put("additional_top_feed_mm", 16); result.put("top_feed_once", true);
        result.put("pad_tcp_payload_identical", true); result.put("takeout_cases", 6);
        System.out.println(new ObjectMapper().writeValueAsString(result));
    }

    private static int occurrences(byte[] bytes, byte[] pattern) {
        int count = 0;
        for (int index = 0; index <= bytes.length - pattern.length; index++) {
            if (Arrays.equals(pattern, Arrays.copyOfRange(bytes, index, index + pattern.length))) count++;
        }
        return count;
    }

    private static PrintRenderRequest fixture(String table, boolean update) {
        var order = new Order();
        order.id = -1L; order.store_id = -1L; order.order_type = "dine_in";
        order.table_no = table; order.submitted_at = LocalDateTime.of(2026, 10, 23, 9, 0);
        order.subtotal_amount = BigDecimal.TEN; order.total_amount = new BigDecimal("11.50");
        var item = new OrderItem();
        item.id = -2L; item.order_id = order.id; item.quantity = 1;
        item.item_sku_snapshot = "beef_chow_mein"; item.category_code_snapshot = "FRIED_NOODLE";
        item.item_name_snapshot_zh = "牛肉炒面"; item.item_name_snapshot_en = "Beef Chow Mein";
        item.status = "submitted"; item.unit_price = BigDecimal.TEN; item.line_amount = BigDecimal.TEN;
        var task = new KitchenTask();
        task.id = -3L; task.order_id = order.id; task.order_item_id = item.id;
        task.station_code = "WOK"; task.item_name_snapshot_zh = "牛炒";
        task.quantity = 1; task.status = "pending"; task.created_at = order.submitted_at;
        var request = new PrintRenderRequest();
        request.order = order; request.order_items = List.of(item); request.order_item_options = List.of();
        request.kitchen_tasks = List.of(task); request.is_update_ticket = update;
        request.happened_at = order.submitted_at;
        return request;
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("HEADER_ACCEPTANCE_FAILED");
    }
}
