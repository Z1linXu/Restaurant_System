package com.restaurant.system.printing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.renderer.PrintMarkup;
import com.restaurant.system.printing.repository.PrintJobAttemptRepository;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.transport.EscPosFontSizeMode;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
