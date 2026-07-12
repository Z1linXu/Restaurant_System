package com.restaurant.system.printing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
        job.status = "CLAIMED";
        job.rendered_text_snapshot = PrintMarkup.doubleHeight("牛肉面 x1");
        job.claimedByDeviceId = 3L;
        job.claimedAt = java.time.LocalDateTime.now();
        job.claimExpiresAt = java.time.LocalDateTime.now().plusMinutes(5);
        job.printedByDeviceId = 3L;
        job.error_code = "OLD_ERROR";
        job.error_message = "Old error";

        PrinterConfig printer = new PrinterConfig();
        printer.id = 10L;
        printer.text_encoding = "GBK";
        printer.font_size = "SMALL";

        when(printJobRepository.findById(job.id)).thenReturn(Optional.of(job));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PrintJob queued = service.markPadDirectQueued(job, printer, "LARGE");
        byte[] payload = Base64.getDecoder().decode(queued.escposPayloadBase64);

        assertEquals("PENDING", queued.status);
        assertEquals("PAD_DIRECT", queued.executionMode);
        assertNull(queued.claimedByDeviceId);
        assertNull(queued.claimedAt);
        assertNull(queued.claimExpiresAt);
        assertNull(queued.printedByDeviceId);
        assertNull(queued.error_code);
        assertNull(queued.error_message);
        assertTrue(containsBytes(payload, EscPosFontSizeMode.LARGE.activate_bytes));
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
