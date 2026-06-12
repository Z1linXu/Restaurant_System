package com.restaurant.system.printing.dto;

import java.util.ArrayList;
import java.util.List;

public class PrinterEncodingTestResponse {

    public boolean success;
    public String recommendation;
    public boolean code_page_command_sent;
    public Integer escpos_code_page;
    public List<PrinterEncodingTestResult> results = new ArrayList<>();

    public static class PrinterEncodingTestResult {
        public String encoding;
        public boolean success;
        public String message;
    }
}
