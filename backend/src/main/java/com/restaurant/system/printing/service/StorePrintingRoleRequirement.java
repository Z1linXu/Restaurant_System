package com.restaurant.system.printing.service;

public record StorePrintingRoleRequirement(
    String moduleCode,
    boolean required,
    String source
) {
}
