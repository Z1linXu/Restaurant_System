package com.restaurant.system.owner.profile;

import java.util.List;

public record StoreProfileValidationResult(
    boolean valid,
    String computedFingerprint,
    List<StoreProfileValidationIssue> issues
) {

    public StoreProfileValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
