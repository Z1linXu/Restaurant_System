package com.restaurant.system.printing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.renderer.FrontdeskReceiptRenderer;
import com.restaurant.system.printing.renderer.GrabReceiptRenderer;
import com.restaurant.system.printing.renderer.HotKitchenReceiptRenderer;
import com.restaurant.system.printing.renderer.PrintMarkup;
import com.restaurant.system.printing.renderer.ReceiptRenderer;
import com.restaurant.system.printing.repository.PrintJobAttemptRepository;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.transport.EscPosFontSizeMode;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrintJobServiceImplTest {

    @Mock
    private PrintJobRepository printJobRepository;
    @Mock
    private PrintJobAttemptRepository printJobAttemptRepository;
    @Mock
    private PrinterConfigRepository printerConfigRepository;

    private PrintJobServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrintJobServiceImpl(
            printJobRepository,
            printJobAttemptRepository,
            printerConfigRepository
        );
    }

    @Test
    void unboundPrinterRetainsTicketButCannotBlockDeviceQueue() {
        PrintJob job = new PrintJob(); job.id = 10L; job.status = "PENDING";
        job.rendered_text_snapshot = "Raw Uber item x1\n外卖";
        PrinterConfig printer = new PrinterConfig(); printer.id = 2L; printer.port = 9100;
        when(printJobRepository.findById(job.id)).thenReturn(Optional.of(job));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(i -> i.getArgument(0));
        PrintJob result = service.markPadDirectQueued(job, printer);
        assertEquals("FAILED", result.status);
        assertEquals("PRINTER_CONFIGURATION_REQUIRED", result.error_code);
        assertEquals("Raw Uber item x1\n外卖", result.rendered_text_snapshot);
        assertTrue(Base64.getDecoder().decode(result.escposPayloadBase64).length > 0);
    }

    @Test
    void padDirectPayloadUsesProvidedFontSize() {
        PrintJob job = new PrintJob();
        job.id = 1L;
        job.status = "PENDING";
        job.rendered_text_snapshot = PrintMarkup.doubleHeight("牛肉面 x1");

        PrinterConfig printer = new PrinterConfig();
        printer.id = 10L;
        printer.ip_address = "127.0.0.1";
        printer.text_encoding = "GBK";
        printer.font_size = "SMALL";

        when(printJobRepository.findById(job.id)).thenReturn(Optional.of(job));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PrintJob queued = service.markPadDirectQueued(job, printer, "LARGE");
        byte[] payload = Base64.getDecoder().decode(queued.escposPayloadBase64);

        assertTrue(containsBytes(payload, EscPosFontSizeMode.LARGE.activate_bytes));
        assertEquals("PAD_DIRECT", queued.executionMode);
        assertEquals("PENDING", queued.status);
        assertEquals(10L, queued.printer_id);
    }

    @ParameterizedTest(name = "{0} update={1} table={2} preserves the first table line in Pad Direct")
    @CsvSource({
        "FRONTDESK_RECEIPT,false,1-右", "FRONTDESK_RECEIPT,true,1-右",
        "GRAB,false,1-右", "GRAB,true,1-右",
        "HOT_KITCHEN,false,1-右", "HOT_KITCHEN,true,1-右",
        "FRONTDESK_RECEIPT,false,1-左", "FRONTDESK_RECEIPT,true,1-左",
        "GRAB,false,1-左", "GRAB,true,1-左",
        "HOT_KITCHEN,false,1-左", "HOT_KITCHEN,true,1-左",
        "FRONTDESK_RECEIPT,false,1里", "FRONTDESK_RECEIPT,true,1里",
        "GRAB,false,1里", "GRAB,true,1里",
        "HOT_KITCHEN,false,1里", "HOT_KITCHEN,true,1里"
    })
    void dineInRendererSnapshotAndPadPayloadKeepTheSameLargeLeftmostTableHeader(String module, boolean update, String tableCode) throws Exception {
        PrintRenderRequest request = dineInRequest(module, update, tableCode);
        ReceiptRenderer renderer;
        if ("FRONTDESK_RECEIPT".equals(module)) {
            renderer = new FrontdeskReceiptRenderer();
        } else if ("GRAB".equals(module)) {
            renderer = new GrabReceiptRenderer();
        } else {
            HotKitchenPrintEligibilityService eligibility = mock(HotKitchenPrintEligibilityService.class);
            when(eligibility.resolveHotKitchenTasks(request)).thenReturn(request.kitchen_tasks);
            renderer = new HotKitchenReceiptRenderer(eligibility);
        }
        String rendered = renderer.render(request);
        String suffix = update ? " (UPDATE)" : "";
        assertEquals(PrintMarkup.large(tableCode) + suffix, PrintMarkup.stripTopFeed(rendered).lines().findFirst().orElseThrow());

        PrintJob preview = new PrintJob();
        preview.id = 31L;
        preview.status = "PENDING";
        PrintJob pad = new PrintJob();
        pad.id = 32L;
        pad.status = "PENDING";
        when(printJobRepository.findById(preview.id)).thenReturn(Optional.of(preview));
        when(printJobRepository.findById(pad.id)).thenReturn(Optional.of(pad));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // MOCK and Pad Direct both store this exact renderer output before executing their transport.
        service.attachRenderedContent(preview, null, rendered);
        service.attachRenderedContent(pad, 10L, rendered);
        PrinterConfig printer = new PrinterConfig();
        printer.id = 10L;
        printer.ip_address = "127.0.0.1";
        printer.text_encoding = "UTF-8";
        printer.font_size = "XS";
        service.markPadDirectQueued(pad, printer, "SMALL");

        assertEquals(rendered, preview.rendered_text_snapshot);
        assertEquals(preview.rendered_text_snapshot, pad.rendered_text_snapshot);
        String firstPayload = pad.escposPayloadBase64;
        service.markPadDirectQueued(pad, printer, "SMALL");
        assertEquals(firstPayload, pad.escposPayloadBase64);
        byte[] payload = Base64.getDecoder().decode(pad.escposPayloadBase64);
        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        prefix.write(new byte[]{0x1B, 0x40}); // Initialize printer before the first line, retaining default left alignment.
        prefix.write(new byte[]{0x1D, 0x50, 0, (byte)254, 0x1B, 0x4A, (byte)160, 0x1D, 0x50, 0, 0});
        prefix.write(EscPosFontSizeMode.LARGE.activate_bytes);
        prefix.write(tableCode.getBytes(StandardCharsets.UTF_8));
        prefix.write(EscPosFontSizeMode.XS.reset_bytes);
        prefix.write(suffix.getBytes(StandardCharsets.UTF_8));
        prefix.write('\n');
        assertArrayEquals(prefix.toByteArray(), Arrays.copyOf(payload, prefix.size()));
        assertFalse(containsBytes(payload, new byte[]{0x1B, 0x61, 0x01}));
        assertFalse(containsBytes(payload, new byte[]{0x1B, 0x61, 0x02}));
        assertFalse(new String(payload, StandardCharsets.UTF_8).contains("[["));
    }

    @ParameterizedTest
    @CsvSource({"1-右,25", "1-左,25", "1里,21"})
    void fullTableAndNormalSizeUpdateMarkerFitConservativePaperWidths(String tableCode, int expectedColumns) {
        // ASCII consumes one base cell; these CJK table characters consume two. LARGE is four cells wide.
        int tableColumns = tableCode.codePoints().map(codePoint -> codePoint < 128 ? 1 : 2).sum();
        int columns = tableColumns * 4 + " (UPDATE)".length();
        assertEquals(expectedColumns, columns);
        assertTrue(columns <= 32, "58 mm / 32-column conservative bound");
        assertTrue(columns <= 42, "80 mm / 42-column conservative bound");
        assertTrue(columns <= 48, "80 mm / 48-column bound");
    }

    @Test
    void frozenWholeLineSizeMarkupKeepsItsExistingPadDirectBytes() throws Exception {
        PrintJob job = new PrintJob();
        job.id = 33L;
        job.status = "PENDING";
        job.rendered_text_snapshot = PrintMarkup.large("1-右 (UPDATE)") + "\n"
            + PrintMarkup.small("小字") + "\n" + PrintMarkup.doubleHeight("正文") + "\nplain";
        when(printJobRepository.findById(job.id)).thenReturn(Optional.of(job));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PrinterConfig printer = new PrinterConfig();
        printer.id = 10L;
        printer.ip_address = "127.0.0.1";
        printer.text_encoding = "UTF-8";
        service.markPadDirectQueued(job, printer, "MEDIUM");

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(new byte[]{0x1B, 0x40});
        writeExpectedSizedLine(expected, EscPosFontSizeMode.LARGE, "1-右 (UPDATE)");
        writeExpectedSizedLine(expected, EscPosFontSizeMode.SMALL, "小字");
        writeExpectedSizedLine(expected, EscPosFontSizeMode.MEDIUM, "正文");
        expected.write("plain\n".getBytes(StandardCharsets.UTF_8));
        expected.write(new byte[]{0x1D, 0x56, 0x41, 0x10});
        assertArrayEquals(expected.toByteArray(), Base64.getDecoder().decode(job.escposPayloadBase64));
    }

    private void writeExpectedSizedLine(ByteArrayOutputStream expected, EscPosFontSizeMode size, String text) throws Exception {
        expected.write(size.activate_bytes);
        expected.write(text.getBytes(StandardCharsets.UTF_8));
        expected.write(EscPosFontSizeMode.XS.reset_bytes);
        expected.write('\n');
    }

    private PrintRenderRequest dineInRequest(String module, boolean update, String tableCode) {
        Order order = new Order();
        order.id = 7L;
        order.order_type = "dine_in";
        order.table_no = tableCode;
        order.subtotal_amount = new BigDecimal("15.00");
        order.total_amount = new BigDecimal("17.25");
        order.submitted_at = LocalDateTime.of(2026, 10, 23, 10, 30);
        OrderItem item = new OrderItem();
        item.id = 8L;
        item.order_id = order.id;
        item.menu_item_id = 9L;
        item.item_name_snapshot_zh = "牛肉炒饭";
        item.item_name_snapshot_en = "Beef fried rice";
        item.category_code_snapshot = "RICE";
        item.quantity = 1;
        item.unit_price = new BigDecimal("15.00");
        item.line_amount = item.unit_price;
        KitchenTask task = new KitchenTask();
        task.id = 10L;
        task.order_id = order.id;
        task.order_item_id = item.id;
        task.station_code = "WOK";
        task.item_name_snapshot_zh = item.item_name_snapshot_zh;
        task.item_name_snapshot_en = item.item_name_snapshot_en;
        task.special_instructions_snapshot = item.item_name_snapshot_zh;
        task.status = "pending";
        task.quantity = 1;
        task.priority = 100;
        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = module;
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = List.of();
        request.kitchen_tasks = List.of(task);
        request.is_update_ticket = update;
        return request;
    }

    @Test
    void durableOriginAppliesToAllModulesAndQueueReplayDoesNotExtendPreference() {
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        try (var scope = com.restaurant.system.printing.security.PrintOriginContext.dispatch(22L, 1L, 7L)) {
            for (String module : new String[]{"GRAB", "FRONTDESK_RECEIPT", "HOT_KITCHEN"}) {
                PrintJob job = service.createPendingJob(7L, 1L, 9L, null, null, module, module, null, "{}");
                assertEquals(22L, job.preferredDeviceId);
                job.id = 99L; job.rendered_text_snapshot = "TEST";
                when(printJobRepository.findById(99L)).thenReturn(Optional.of(job));
                PrinterConfig printer = new PrinterConfig(); printer.id = 1L; printer.ip_address = "127.0.0.1"; printer.text_encoding = "GBK";
                service.markPadDirectQueued(job, printer, "SMALL");
                var deadline = job.preferredDeviceUntil;
                assertTrue(deadline.isAfter(java.time.LocalDateTime.now().plusSeconds(8)));
                service.markPadDirectQueued(job, printer, "SMALL");
                assertEquals(deadline, job.preferredDeviceUntil);
            }
        }
        assertEquals(null, com.restaurant.system.printing.security.PrintOriginContext.deviceFor(1L, 7L));
    }

    @Test
    void persistedDispatchSourceReturnsExistingJobWithoutCreatingDuplicate() {
        PrintJob existing = new PrintJob();
        existing.id = 81L;
        existing.dispatchSourceKey = "submit:9:GRAB";
        when(printJobRepository.findByDispatchSourceKey(existing.dispatchSourceKey)).thenReturn(Optional.of(existing));

        PrintJob result = service.createPendingJob(
            7L,
            1L,
            9L,
            null,
            null,
            "GRAB",
            "GRAB",
            null,
            "{}",
            existing.dispatchSourceKey
        );

        assertEquals(existing, result);
        verify(printJobRepository, never()).save(any(PrintJob.class));
    }

    @Test
    void acknowledgeAttentionStoresCurrentStateWithoutChangingPrintStatus() {
        PrintJob job = new PrintJob();
        job.id = 91L;
        job.status = "FAILED";
        job.retry_count = 1;
        job.error_code = "DISPATCH_ERROR";
        when(printJobRepository.findById(job.id)).thenReturn(Optional.of(job));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PrintJob acknowledged = service.acknowledgeAttention(job.id, 7L, "已人工通知厨房");

        assertEquals("FAILED", acknowledged.status);
        assertEquals("DISPATCH_ERROR", acknowledged.error_code);
        assertEquals(7L, acknowledged.attentionAcknowledgedBy);
        assertEquals("FAILED", acknowledged.attentionAcknowledgedStatus);
        assertEquals(1, acknowledged.attentionAcknowledgedRetryCount);
        assertEquals("DISPATCH_ERROR", acknowledged.attentionAcknowledgedErrorCode);
        assertEquals("已人工通知厨房", acknowledged.attentionAcknowledgementNote);
        verify(printJobRepository).save(job);
    }

    private boolean containsBytes(byte[] payload, byte[] expected) {
        for (int index = 0; index <= payload.length - expected.length; index += 1) {
            boolean matches = true;
            for (int offset = 0; offset < expected.length; offset += 1) {
                if (payload[index + offset] != expected[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }
}
