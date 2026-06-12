package com.restaurant.system.printing.dto;

import java.util.ArrayList;
import java.util.List;

public class GrabFontTestResponse {

    public boolean success;
    public List<GrabFontTestResult> results = new ArrayList<>();

    public static class GrabFontTestResult {
        public String test_mode;
        public String command_bytes;
        public boolean success;
        public String message;
    }
}
