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
    void padDirectPayloadUsesProvidedFontSize() {
        PrintJob job = new PrintJob();
        job.id = 1L;
        job.rendered_text_snapshot = PrintMarkup.doubleHeight("牛肉面 x1");

        PrinterConfig printer = new PrinterConfig();
        printer.id = 10L;
        printer.text_encoding = "GBK";
        printer.font_size = "SMALL";

        when(printJobRepository.findById(job.id)).thenReturn(Optional.of(job));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PrintJob queued = service.markPadDirectQueued(job, printer, "LARGE");
        byte[] payload = Base64.getDecoder().decode(queued.escposPayloadBase64);

        assertTrue(containsBytes(payload, EscPosFontSizeMode.LARGE.activate_bytes));
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
