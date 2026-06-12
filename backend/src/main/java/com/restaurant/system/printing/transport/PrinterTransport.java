package com.restaurant.system.printing.transport;

import com.restaurant.system.printing.entity.PrinterConfig;
import java.util.List;

public interface PrinterTransport {

    boolean supports(String printerType);

    void print(PrinterConfig printerConfig, String content);

    default void print(PrinterConfig printerConfig, String content, String overrideEncoding, Integer overrideCodePage) {
        print(printerConfig, content);
    }

    default void print(
        PrinterConfig printerConfig,
        String content,
        String overrideEncoding,
        Integer overrideCodePage,
        String overrideFontSize
    ) {
        print(printerConfig, content, overrideEncoding, overrideCodePage);
    }

    default void printDiagnosticTicket(
        PrinterConfig printerConfig,
        List<String> headerLines,
        List<String> emphasizedLines,
        List<String> footerLines,
        String overrideEncoding,
        Integer overrideCodePage,
        EscPosFontTestMode fontTestMode
    ) {
        throw new UnsupportedOperationException("Diagnostic ticket printing is not supported by this transport");
    }
}
