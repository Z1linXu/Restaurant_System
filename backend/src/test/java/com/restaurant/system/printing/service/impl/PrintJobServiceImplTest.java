package com.restaurant.system.printing.service.impl;

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
