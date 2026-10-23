package com.restaurant.system.printing.transport;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.CloudPrintingGuard;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.renderer.PrintMarkup;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({"1-右,false", "1-右,true", "1-左,false", "1-左,true", "1里,false", "1里,true"})
    void sendsLargeTableHeaderAsTheFirstLeftmostTextWithoutChangingFontCommands(String tableCode, boolean update) throws Exception {
        String suffix = update ? " (UPDATE)" : "";
        String body = "牛肉炒饭 x1";
        byte[] actual = printAndCapture(PrintMarkup.large(tableCode) + suffix + "\n" + PrintMarkup.doubleHeight(body), "SMALL");

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(new byte[]{0x1B, 0x40});
        expected.write(EscPosFontSizeMode.LARGE.activate_bytes);
        expected.write(tableCode.getBytes(StandardCharsets.UTF_8));
        expected.write(EscPosFontSizeMode.XS.reset_bytes);
        expected.write(suffix.getBytes(StandardCharsets.UTF_8));
        expected.write('\n');
        expected.write(EscPosFontSizeMode.SMALL.activate_bytes);
        expected.write(body.getBytes(StandardCharsets.UTF_8));
        expected.write(EscPosFontSizeMode.XS.reset_bytes);
        expected.write('\n');
        expected.write(new byte[]{0x1D, 0x56, 0x41, 0x10});
        assertArrayEquals(expected.toByteArray(), actual);
    }

    @Test
    void frozenWholeLineSizeMarkupKeepsItsExistingTcpBytes() throws Exception {
        byte[] actual = printAndCapture(PrintMarkup.large("1-右 (UPDATE)") + "\n"
            + PrintMarkup.small("小字") + "\n" + PrintMarkup.doubleHeight("正文") + "\nplain", "MEDIUM");
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(new byte[]{0x1B, 0x40});
        writeExpectedSizedLine(expected, EscPosFontSizeMode.LARGE, "1-右 (UPDATE)");
        writeExpectedSizedLine(expected, EscPosFontSizeMode.SMALL, "小字");
        writeExpectedSizedLine(expected, EscPosFontSizeMode.MEDIUM, "正文");
        expected.write("plain\n".getBytes(StandardCharsets.UTF_8));
        expected.write(new byte[]{0x1D, 0x56, 0x41, 0x10});
        assertArrayEquals(expected.toByteArray(), actual);
    }

    private void writeExpectedSizedLine(ByteArrayOutputStream expected, EscPosFontSizeMode size, String text) throws Exception {
        expected.write(size.activate_bytes);
        expected.write(text.getBytes(StandardCharsets.UTF_8));
        expected.write(EscPosFontSizeMode.XS.reset_bytes);
        expected.write('\n');
    }

    private byte[] printAndCapture(String content, String fontSize) throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(5000);
            CompletableFuture<byte[]> captured = CompletableFuture.supplyAsync(() -> {
                try (var socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    return socket.getInputStream().readAllBytes();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            PrinterConfig printer = printer(listener.getInetAddress().getHostAddress());
            printer.port = listener.getLocalPort();
            printer.font_size = "XS";
            EscPosTcpPrinterTransport transport = new EscPosTcpPrinterTransport(new CloudPrintingGuard(new MockEnvironment()));

            transport.print(printer, content, "UTF-8", null, fontSize);
            return captured.get(5, TimeUnit.SECONDS);
        }
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
