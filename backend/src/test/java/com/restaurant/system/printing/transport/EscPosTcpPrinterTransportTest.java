package com.restaurant.system.printing.transport;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.CloudPrintingGuard;
import com.restaurant.system.printing.entity.PrinterConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EscPosTcpPrinterTransportTest {

    @Test
    void cloudProfileBlocksPrivatePrinterBeforeTcpSocket() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("cloud");
        EscPosTcpPrinterTransport transport = new EscPosTcpPrinterTransport(new CloudPrintingGuard(environment));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> transport.print(printer("192.168.2.200"), "test")
        );

        assertTrue(exception.getMessage().contains("Cloud server cannot directly connect"));
    }

    private PrinterConfig printer(String host) {
        PrinterConfig printer = new PrinterConfig();
        printer.ip_address = host;
        printer.port = 9100;
        printer.text_encoding = "UTF-8";
        printer.printer_type = "ESC_POS_TCP";
        return printer;
    }
}
