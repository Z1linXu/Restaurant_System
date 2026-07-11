package com.restaurant.system.printing.transport;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.CloudPrintingGuard;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.renderer.PrintMarkup;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EscPosTcpPrinterTransport implements PrinterTransport {

    private final CloudPrintingGuard cloudPrintingGuard;

    public EscPosTcpPrinterTransport(CloudPrintingGuard cloudPrintingGuard) {
        this.cloudPrintingGuard = cloudPrintingGuard;
    }

    @Override
    public boolean supports(String printerType) {
        return "ESC_POS_TCP".equalsIgnoreCase(printerType);
    }

    @Override
    public void print(PrinterConfig printerConfig, String content) {
        print(printerConfig, content, printerConfig.text_encoding, printerConfig.escpos_code_page);
    }

    @Override
    public void print(PrinterConfig printerConfig, String content, String overrideEncoding, Integer overrideCodePage) {
        print(printerConfig, content, overrideEncoding, overrideCodePage, printerConfig.font_size);
    }

    @Override
    public void print(
        PrinterConfig printerConfig,
        String content,
        String overrideEncoding,
        Integer overrideCodePage,
        String overrideFontSize
    ) {
        cloudPrintingGuard.assertBackendTcpAllowed(printerConfig);
        try (Socket socket = new Socket()) {
            int timeout = printerConfig.timeout_ms == null ? 3000 : printerConfig.timeout_ms;
            socket.connect(
                new InetSocketAddress(printerConfig.ip_address, printerConfig.port == null ? 9100 : printerConfig.port),
                timeout
            );
            socket.setSoTimeout(timeout);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(new byte[] {0x1B, 0x40});
            if (overrideCodePage != null) {
                outputStream.write(new byte[] {0x1B, 0x74, (byte) (overrideCodePage & 0xFF)});
            }
            writeContent(outputStream, content, resolveCharset(overrideEncoding), overrideFontSize);
            outputStream.write(new byte[] {0x1D, 0x56, 0x41, 0x10});
            outputStream.flush();
        } catch (Exception exception) {
            throw new BusinessException("Printer transport failed: " + exception.getMessage());
        }
    }

    @Override
    public void printDiagnosticTicket(
        PrinterConfig printerConfig,
        List<String> headerLines,
        List<String> emphasizedLines,
        List<String> footerLines,
        String overrideEncoding,
        Integer overrideCodePage,
        EscPosFontTestMode fontTestMode
    ) {
        cloudPrintingGuard.assertBackendTcpAllowed(printerConfig);
        try (Socket socket = new Socket()) {
            int timeout = printerConfig.timeout_ms == null ? 3000 : printerConfig.timeout_ms;
            socket.connect(
                new InetSocketAddress(printerConfig.ip_address, printerConfig.port == null ? 9100 : printerConfig.port),
                timeout
            );
            socket.setSoTimeout(timeout);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(new byte[] {0x1B, 0x40});
            if (overrideCodePage != null) {
                outputStream.write(new byte[] {0x1B, 0x74, (byte) (overrideCodePage & 0xFF)});
            }

            Charset charset = resolveCharset(overrideEncoding);
            writePlainLines(outputStream, headerLines, charset);
            outputStream.write(fontTestMode.activate_bytes);
            writePlainLines(outputStream, emphasizedLines, charset);
            outputStream.write(fontTestMode.reset_bytes);
            writePlainLines(outputStream, footerLines, charset);
            outputStream.write(new byte[] {0x1D, 0x56, 0x41, 0x10});
            outputStream.flush();
        } catch (Exception exception) {
            throw new BusinessException("Printer transport failed: " + exception.getMessage());
        }
    }

    private Charset resolveCharset(String configuredEncoding) {
        if (configuredEncoding == null || configuredEncoding.isBlank()) {
            return Charset.forName("GBK");
        }
        try {
            return Charset.forName(configuredEncoding);
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private void writeContent(OutputStream outputStream, String content, Charset charset, String fontSize) throws Exception {
        String[] lines = content.split("\\n", -1);
        EscPosFontSizeMode fontSizeMode = EscPosFontSizeMode.fromConfig(fontSize);
        for (String rawLine : lines) {
            boolean doubleHeight = rawLine.contains(PrintMarkup.DOUBLE_HEIGHT_OPEN) && rawLine.contains(PrintMarkup.DOUBLE_HEIGHT_CLOSE);
            boolean large = rawLine.contains(PrintMarkup.LARGE_OPEN) && rawLine.contains(PrintMarkup.LARGE_CLOSE);
            boolean small = rawLine.contains(PrintMarkup.SMALL_OPEN) && rawLine.contains(PrintMarkup.SMALL_CLOSE);
            String line = rawLine
                .replace(PrintMarkup.DOUBLE_HEIGHT_OPEN, "")
                .replace(PrintMarkup.DOUBLE_HEIGHT_CLOSE, "")
                .replace(PrintMarkup.LARGE_OPEN, "")
                .replace(PrintMarkup.LARGE_CLOSE, "")
                .replace(PrintMarkup.SMALL_OPEN, "")
                .replace(PrintMarkup.SMALL_CLOSE, "");

            if (large) {
                outputStream.write(EscPosFontSizeMode.LARGE.activate_bytes);
            } else if (small) {
                outputStream.write(EscPosFontSizeMode.SMALL.activate_bytes);
            } else if (doubleHeight) {
                outputStream.write(fontSizeMode.activate_bytes);
            }
            outputStream.write(line.getBytes(charset));
            if (large || small || doubleHeight) {
                outputStream.write(EscPosFontSizeMode.XS.reset_bytes);
            }
            outputStream.write('\n');
        }
    }

    private void writePlainLines(OutputStream outputStream, List<String> lines, Charset charset) throws Exception {
        for (String line : lines) {
            outputStream.write(line.getBytes(charset));
            outputStream.write('\n');
        }
    }
}
