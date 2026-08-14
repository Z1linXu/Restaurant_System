package com.restaurant.system.modules;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ModuleDependencyValidator {

    private final ModuleCatalogDefinition catalog;
    private final ModuleDependencyGraph graph;
    private final HardwareCapabilityCatalogDefinition hardwareCatalog;

    public ModuleDependencyValidator(ModuleCatalogDefinition catalog, ModuleDependencyGraph graph) {
        this(catalog, graph, new ModuleContractLoader().loadHardwareCatalog());
    }

    public ModuleDependencyValidator(
        ModuleCatalogDefinition catalog,
        ModuleDependencyGraph graph,
        HardwareCapabilityCatalogDefinition hardwareCatalog
    ) {
        this.catalog = catalog;
        this.graph = graph;
        this.hardwareCatalog = hardwareCatalog;
    }

    public static ModuleDependencyValidator loadDefault() {
        ModuleContractLoader loader = new ModuleContractLoader();
        return new ModuleDependencyValidator(loader.loadCatalog(), loader.loadDependencyGraph());
    }

    public ModuleValidationResult validate(ModuleConfigurationInput input) {
        List<ModuleValidationIssue> issues = new ArrayList<>(validateGraphDefinition().issues());
        if (!issues.isEmpty()) {
            return new ModuleValidationResult(false, List.copyOf(issues));
        }

        for (String moduleKey : input.moduleStates().keySet()) {
            if (!catalog.hasModule(moduleKey)) {
                issues.add(issue(
                    ModuleValidationCode.UNKNOWN_MODULE,
                    moduleKey,
                    null,
                    "Unknown module state is not allowed"
                ));
            }
        }

        for (String hardwareCapability : input.hardwareCapabilities()) {
            if (!hardwareCatalog.supports(hardwareCapability)) {
                issues.add(issue(
                    ModuleValidationCode.UNKNOWN_HARDWARE_CAPABILITY,
                    null,
                    hardwareCapability,
                    "Unknown hardware capability is not allowed"
                ));
            }
        }

        for (String coreModuleKey : catalog.coreModuleKeys()) {
            if (stateFor(input, coreModuleKey) == ModuleState.DISABLED) {
                issues.add(issue(
                    ModuleValidationCode.CORE_MODULE_DISABLED,
                    coreModuleKey,
                    null,
                    "Core module must remain enabled"
                ));
            }
        }

        for (ModuleDependencyRule rule : graph.dependencies()) {
            if (!catalog.hasModule(rule.sourceModule()) || stateFor(input, rule.sourceModule()) != ModuleState.ENABLED) {
                continue;
            }
            ModuleDependencyType type = ModuleDependencyType.from(rule.type()).orElseThrow();
            switch (type) {
                case REQUIRES -> requireModule(input, issues, rule);
                case CONFLICTS_WITH -> rejectConflict(input, issues, rule);
                case REQUIRES_ENVIRONMENT_CAPABILITY -> requireEnvironmentCapability(input, issues, rule);
                case REQUIRES_HARDWARE_CAPABILITY -> requireHardwareCapability(input, issues, rule);
            }
        }

        return new ModuleValidationResult(issues.isEmpty(), List.copyOf(issues));
    }

    public ModuleValidationResult validateGraphDefinition() {
        List<ModuleValidationIssue> issues = new ArrayList<>();
        for (ModuleDependencyRule rule : graph.dependencies()) {
            Optional<ModuleDependencyType> type = ModuleDependencyType.from(rule.type());
            if (!catalog.hasModule(rule.sourceModule())) {
                issues.add(issue(
                    ModuleValidationCode.INVALID_DEPENDENCY_GRAPH,
                    rule.sourceModule(),
                    rule.target(),
                    "Dependency source module is unknown"
                ));
                continue;
            }
            if (type.isEmpty()) {
                issues.add(issue(
                    ModuleValidationCode.INVALID_DEPENDENCY_GRAPH,
                    rule.sourceModule(),
                    rule.target(),
                    "Dependency type is unknown: " + rule.type()
                ));
                continue;
            }
            switch (type.get()) {
                case REQUIRES, CONFLICTS_WITH -> {
                    if (!catalog.hasModule(rule.target())) {
                        issues.add(issue(
                            ModuleValidationCode.INVALID_DEPENDENCY_GRAPH,
                            rule.sourceModule(),
                            rule.target(),
                            "Dependency target module is unknown"
                        ));
                    }
                }
                case REQUIRES_ENVIRONMENT_CAPABILITY -> {
                    if (!catalog.environmentCapabilities().contains(rule.target())) {
                        issues.add(issue(
                            ModuleValidationCode.INVALID_DEPENDENCY_GRAPH,
                            rule.sourceModule(),
                            rule.target(),
                            "Dependency target environment capability is unknown"
                        ));
                    }
                }
                case REQUIRES_HARDWARE_CAPABILITY -> {
                    if (!hardwareCatalog.supports(rule.target())) {
                        issues.add(issue(
                            ModuleValidationCode.INVALID_DEPENDENCY_GRAPH,
                            rule.sourceModule(),
                            rule.target(),
                            "Dependency target hardware capability is unknown"
                        ));
                    }
                }
            }
        }
        issues.addAll(findRequireCycles());
        return new ModuleValidationResult(issues.isEmpty(), List.copyOf(issues));
    }

    private List<ModuleValidationIssue> findRequireCycles() {
        Map<String, List<String>> adjacency = graph.dependencies().stream()
            .filter(rule -> "REQUIRES".equals(rule.type()))
            .filter(rule -> catalog.hasModule(rule.sourceModule()) && catalog.hasModule(rule.target()))
            .collect(
                java.util.stream.Collectors.groupingBy(
                    ModuleDependencyRule::sourceModule,
                    java.util.stream.Collectors.mapping(ModuleDependencyRule::target, java.util.stream.Collectors.toList())
                )
            );
        List<ModuleValidationIssue> issues = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> active = new LinkedHashSet<>();
        for (String moduleKey : catalog.moduleKeys()) {
            detectCycle(moduleKey, adjacency, visited, active, issues);
        }
        return issues;
    }

    private void detectCycle(
        String moduleKey,
        Map<String, List<String>> adjacency,
        Set<String> visited,
        Set<String> active,
        List<ModuleValidationIssue> issues
    ) {
        if (active.contains(moduleKey)) {
            issues.add(issue(
                ModuleValidationCode.INVALID_DEPENDENCY_GRAPH,
                moduleKey,
                null,
                "Dependency cycle detected"
            ));
            return;
        }
        if (visited.contains(moduleKey)) {
            return;
        }
        active.add(moduleKey);
        for (String child : adjacency.getOrDefault(moduleKey, List.of())) {
            detectCycle(child, adjacency, visited, active, issues);
        }
        active.remove(moduleKey);
        visited.add(moduleKey);
    }

    private void requireModule(
        ModuleConfigurationInput input,
        List<ModuleValidationIssue> issues,
        ModuleDependencyRule rule
    ) {
        if (stateFor(input, rule.target()) == ModuleState.DISABLED) {
            issues.add(issue(
                ModuleValidationCode.REQUIRED_MODULE_DISABLED,
                rule.sourceModule(),
                rule.target(),
                "Required module is disabled"
            ));
        }
    }

    private void rejectConflict(
        ModuleConfigurationInput input,
        List<ModuleValidationIssue> issues,
        ModuleDependencyRule rule
    ) {
        if (stateFor(input, rule.target()) == ModuleState.ENABLED) {
            issues.add(issue(
                ModuleValidationCode.MODULE_CONFLICT,
                rule.sourceModule(),
                rule.target(),
                "Conflicting module is enabled"
            ));
        }
    }

    private void requireEnvironmentCapability(
        ModuleConfigurationInput input,
        List<ModuleValidationIssue> issues,
        ModuleDependencyRule rule
    ) {
        if (!input.environmentCapabilities().contains(rule.target())) {
            issues.add(issue(
                ModuleValidationCode.ENVIRONMENT_CAPABILITY_MISSING,
                rule.sourceModule(),
                rule.target(),
                "Environment capability is missing"
            ));
        }
    }

    private void requireHardwareCapability(
        ModuleConfigurationInput input,
        List<ModuleValidationIssue> issues,
        ModuleDependencyRule rule
    ) {
        Set<String> inputCapabilities = canonicalHardwareCapabilities(input.hardwareCapabilities());
        Set<String> requiredCapabilities = hardwareCatalog.canonicalKeys(rule.target());
        if (requiredCapabilities.isEmpty() || inputCapabilities.stream().noneMatch(requiredCapabilities::contains)) {
            issues.add(issue(
                ModuleValidationCode.HARDWARE_CAPABILITY_MISSING,
                rule.sourceModule(),
                rule.target(),
                "Hardware capability is missing"
            ));
        }
    }

    private Set<String> canonicalHardwareCapabilities(Set<String> hardwareCapabilities) {
        Set<String> canonical = new LinkedHashSet<>();
        for (String capability : hardwareCapabilities == null ? Set.<String>of() : hardwareCapabilities) {
            Set<String> canonicalKeys = hardwareCatalog.canonicalKeys(capability);
            canonical.addAll(canonicalKeys);
        }
        return Set.copyOf(canonical);
    }

    private ModuleState stateFor(ModuleConfigurationInput input, String moduleKey) {
        return input.moduleStates().getOrDefault(moduleKey, catalog.defaultState(moduleKey));
    }

    private ModuleValidationIssue issue(
        ModuleValidationCode code,
        String moduleKey,
        String target,
        String message
    ) {
        return new ModuleValidationIssue(code, moduleKey, target, message);
    }
}
