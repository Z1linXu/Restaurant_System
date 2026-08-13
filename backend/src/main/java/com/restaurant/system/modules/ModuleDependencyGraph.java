package com.restaurant.system.modules;

import java.util.List;

public record ModuleDependencyGraph(
    String graphVersion,
    String catalogVersion,
    List<ModuleDependencyRule> dependencies
) {
}
