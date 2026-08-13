package com.restaurant.system.modules;

public record ModuleDependencyRule(
    String sourceModule,
    String type,
    String target
) {
}
