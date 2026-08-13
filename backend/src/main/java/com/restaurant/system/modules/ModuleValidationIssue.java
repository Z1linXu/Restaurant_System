package com.restaurant.system.modules;

public record ModuleValidationIssue(
    ModuleValidationCode code,
    String moduleKey,
    String target,
    String message
) {
}
