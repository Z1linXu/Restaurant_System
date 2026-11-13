package com.restaurant.system.printing.renderer;

import static org.junit.jupiter.api.Assertions.*;
import com.restaurant.system.order.entity.*;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.printing.dto.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class AlloGrabNotesTest {
    private final GrabReceiptRenderer renderer = new GrabReceiptRenderer();
    private PrintRenderRequest request(String... codes) {
        PrintRenderRequest r = new PrintRenderRequest();
        r.order = new Order(); r.order.id = 10L; r.order.order_type = "dine_in"; r.order.table_no = "TEST 1";
        r.order.subtotal_amount = r.order.total_amount = BigDecimal.valueOf(codes.length);
        r.order_items = new ArrayList<>(); r.kitchen_tasks = new ArrayList<>(); r.order_item_options = new ArrayList<>();
        ReceiptPresentation p = new ReceiptPresentation(); r.receipt_context = p;
        p.language = "zh-CN"; p.restaurant_name = "Allô Nouilles"; p.kitchen_station_codes = List.of("BACK_KITCHEN");
        p.subtotal = p.total = BigDecimal.valueOf(codes.length);
        for (int i = 0; i < codes.length; i++) {
            OrderItem item = new OrderItem(); item.id = (long)i+1; item.item_sku_snapshot = codes[i];
            item.item_name_snapshot_zh = codes[i] + "-line" + i; item.quantity = 1; item.unit_price = item.line_amount = BigDecimal.ONE;
            r.order_items.add(item); p.order_item_ids.add(item.id); p.item_names.put(""+item.id, item.item_name_snapshot_zh);
            KitchenTask task = new KitchenTask(); task.id = (long)i+1; task.order_item_id = item.id;
            task.station_code = "BACK_KITCHEN"; task.status = "pending"; task.quantity = 1; r.kitchen_tasks.add(task);
        }
        return r;
    }
    @Test void naturalOrderUsesSkuAndKeepsSourceOrderAndDistinctLines() {
        var r = request("AN_E2","AN_C4","AN_B1","AN_A7","AN_E1","AN_A3","AN_A10","AN_A2","bad","AN_E2");
        r.order_items.get(0).notes = "第一份"; r.order_items.get(9).notes = "Second portion";
        var before = r.order_items.stream().map(i -> i.item_sku_snapshot).toList();
        String text = renderer.render(r);
        String[] expected = {"AN_A2-line7","AN_A3-line5","AN_A7-line3","AN_A10-line6","AN_B1-line2","AN_C4-line1","AN_E1-line4","AN_E2-line0","AN_E2-line9","bad-line8"};
        int at = -1; for (String line : expected) { int next = text.indexOf(line); assertTrue(next > at, line); at = next; }
        assertTrue(text.contains("第一份")); assertTrue(text.contains("Second portion"));
        assertEquals(before,r.order_items.stream().map(i -> i.item_sku_snapshot).toList());
        assertFalse(text.contains("Allô Nouilles"));
        String receipt = new FrontdeskReceiptRenderer().render(r);
        assertTrue(receipt.contains("Allô Nouilles"));
        assertTrue(receipt.indexOf("AN_E2-line0") < receipt.indexOf("AN_A3-line5"));
    }
    @Test void frozenWholeNoteAppearsOnceAfterItemNotesAndSupportsNewlinesAndRealEncoding() throws Exception {
        var r = request("AN_C4","AN_B1"); r.order_items.get(0).notes="不要酱汁"; r.order_items.get(1).notes="少辣";
        r.receipt_context.order_notes_captured=true; r.receipt_context.order_notes="请所有菜一起出，谢谢。\nPlease serve together.";
        String text = renderer.render(r);
        assertTrue(text.indexOf("少辣") < text.indexOf("不要酱汁"));
        assertTrue(text.indexOf("不要酱汁") < text.indexOf("整单备注"));
        assertEquals(1,text.split("整单备注",-1).length-1); assertTrue(text.contains("Please serve together."));
        var transport = new com.restaurant.system.printing.transport.EscPosTcpPrinterTransport(null);
        var bytes = new java.io.ByteArrayOutputStream();
        var encode = transport.getClass().getDeclaredMethod("writeContent",java.io.OutputStream.class,String.class,java.nio.charset.Charset.class,String.class);
        encode.setAccessible(true); encode.invoke(transport,bytes,text,java.nio.charset.StandardCharsets.UTF_8,"MEDIUM");
        String encoded = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(encoded.contains("Allô Nouilles")); assertFalse(encoded.contains("[[DOUBLE_HEIGHT]]"));
        assertTrue(encoded.indexOf("AN_B1") < encoded.indexOf("AN_C4")); assertTrue(encoded.contains("请所有菜一起出，谢谢。"));
    }
    @Test void oldContextsDoNotReadCurrentNotesAndEmptyInitialNoteHasNoRegion() {
        var r=request("AN_B1"); r.order.order_notes="New current note must not leak";
        assertFalse(renderer.render(r).contains("New current"));
        r.receipt_context.order_notes_captured=true; r.receipt_context.order_notes=" ";
        assertFalse(renderer.render(r).contains("整单备注"));
    }
    @Test void noteOnlyUpdateAndExplicitClearArePrintableWithoutResendingOldItems() {
        var r=request(); r.is_update_ticket=true; r.receipt_context.order_notes_captured=true;
        r.receipt_context.order_notes_changed=true; r.receipt_context.order_notes_only=true;
        r.receipt_context.order_notes="Kitchen note";
        String text=renderer.render(r); assertTrue(text.contains("备注更新")); assertTrue(text.contains("整单备注已更新")); assertTrue(text.contains("Kitchen note"));
        r.receipt_context.order_notes=null; text=renderer.render(r); assertTrue(text.contains("整单备注已清除"));
        r.receipt_context.order_notes_changed=false; assertThrows(RuntimeException.class,()->renderer.render(r));
    }
    @Test void updateContainsOnlyGivenBatchAndOneCurrentWholeNote() {
        var r=request("AN_E2","AN_A3"); r.is_update_ticket=true; r.receipt_context.order_notes_captured=true;
        r.receipt_context.order_notes="Same whole-order note";
        String text=renderer.render(r); assertTrue(text.indexOf("AN_A3")<text.indexOf("AN_E2"));
        assertTrue(text.contains("整单备注（当前）")); assertEquals(1,text.split("Same whole-order note",-1).length-1);
    }
}
