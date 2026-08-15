package com.restaurant.system.printing.rules;

import java.util.List;

public record PrintingDisplayRuleValidationResult(
    boolean valid,
    String fingerprintSha256,
    List<PrintingDisplayRuleValidationIssue> issues
) {
}
