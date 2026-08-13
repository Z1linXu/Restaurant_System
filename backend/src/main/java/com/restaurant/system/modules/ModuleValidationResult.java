package com.restaurant.system.modules;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record ModuleValidationResult(
    boolean valid,
    List<ModuleValidationIssue> issues
) {
    public Set<ModuleValidationCode> issueCodes() {
        return issues.stream()
            .map(ModuleValidationIssue::code)
            .collect(Collectors.toUnmodifiableSet());
    }
}
