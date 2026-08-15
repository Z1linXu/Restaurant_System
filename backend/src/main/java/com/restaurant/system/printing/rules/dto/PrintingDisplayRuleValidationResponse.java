package com.restaurant.system.printing.rules.dto;

import com.restaurant.system.printing.rules.PrintingDisplayRuleValidationIssue;
import java.util.List;

public class PrintingDisplayRuleValidationResponse {
    public boolean valid;
    public String fingerprint_sha256;
    public List<PrintingDisplayRuleValidationIssue> issues;
}
