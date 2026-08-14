package com.restaurant.system.modules;

import com.restaurant.system.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreModuleAccessEvaluator {

    public static final String MODULE_DISABLED = "MODULE_DISABLED";
    public static final String MODULE_CONFIGURATION_INVALID = "MODULE_CONFIGURATION_INVALID";
    public static final String MODULE_ENVIRONMENT_CAPABILITY_MISSING = "MODULE_ENVIRONMENT_CAPABILITY_MISSING";

    private final StoreModuleRepository storeModuleRepository;
    private final StoreModuleCapabilityProvider capabilityProvider;
    private final ModuleCatalogDefinition catalog;
    private final ModuleDependencyGraph dependencyGraph;

    public StoreModuleAccessEvaluator(
        StoreModuleRepository storeModuleRepository,
        StoreModuleCapabilityProvider capabilityProvider
    ) {
        this(
            storeModuleRepository,
            capabilityProvider,
            new ModuleContractLoader()
        );
    }

    StoreModuleAccessEvaluator(
        StoreModuleRepository storeModuleRepository,
        StoreModuleCapabilityProvider capabilityProvider,
        ModuleContractLoader loader
    ) {
        this.storeModuleRepository = storeModuleRepository;
        this.capabilityProvider = capabilityProvider;
        this.catalog = loader.loadCatalog();
        this.dependencyGraph = loader.loadDependencyGraph();
    }

    @Transactional(readOnly = true)
    public boolean isModuleEnabled(Long storeId, String moduleKey) {
        return evaluateCapability(storeId, moduleKey).storeModuleEnabled();
    }

    @Transactional(readOnly = true)
    public void requireModuleEnabled(Long storeId, String moduleKey) {
        evaluateCapability(storeId, moduleKey).requireModuleEnabled();
    }

    @Transactional(readOnly = true)
    public StoreModuleAccessEvaluation evaluateCapability(Long storeId, String moduleKey) {
        if (storeId == null) {
            throw new BusinessException("STORE_ID_REQUIRED");
        }
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        if (!catalog.hasModule(normalizedModuleKey)) {
            return denied(
                storeId,
                normalizedModuleKey,
                false,
                false,
                false,
                true,
                MODULE_CONFIGURATION_INVALID,
                "Unknown Store module: " + normalizedModuleKey,
                List.of(),
                List.of("UNKNOWN_MODULE")
            );
        }

        Map<String, StoreModule> modulesByKey = indexModules(storeModuleRepository.findAllByStoreIdOrderByIdAsc(storeId));
        StoreModule target = modulesByKey.get(normalizedModuleKey);
        if (target == null) {
            return denied(
                storeId,
                normalizedModuleKey,
                true,
                false,
                false,
                true,
                MODULE_CONFIGURATION_INVALID,
                "Store module configuration is missing: " + normalizedModuleKey,
                List.of(),
                List.of("STORE_MODULE_MISSING")
            );
        }
        if (!Boolean.TRUE.equals(target.enabled)) {
            return denied(
                storeId,
                normalizedModuleKey,
                true,
                true,
                false,
                true,
                MODULE_DISABLED,
                "Module disabled for this Store: " + normalizedModuleKey,
                List.of(),
                List.of()
            );
        }

        List<String> configurationIssues = configurationIssues(normalizedModuleKey, modulesByKey);
        if (!configurationIssues.isEmpty()) {
            return denied(
                storeId,
                normalizedModuleKey,
                true,
                true,
                true,
                true,
                MODULE_CONFIGURATION_INVALID,
                "Store module configuration is invalid for " + normalizedModuleKey + ": " + String.join(",", configurationIssues),
                List.of(),
                configurationIssues
            );
        }

        Set<String> environmentCapabilities = capabilityProvider.environmentCapabilities(storeId);
        List<String> missingEnvironmentCapabilities = requiredEnvironmentCapabilities(normalizedModuleKey).stream()
            .filter(capability -> !environmentCapabilities.contains(capability))
            .sorted(Comparator.naturalOrder())
            .toList();
        if (!missingEnvironmentCapabilities.isEmpty()) {
            return denied(
                storeId,
                normalizedModuleKey,
                true,
                true,
                true,
                false,
                MODULE_ENVIRONMENT_CAPABILITY_MISSING,
                "Environment capability missing for " + normalizedModuleKey + ": " + String.join(",", missingEnvironmentCapabilities),
                missingEnvironmentCapabilities,
                List.of("ENVIRONMENT_CAPABILITY_MISSING")
            );
        }

        return new StoreModuleAccessEvaluation(
            storeId,
            normalizedModuleKey,
            true,
            true,
            true,
            true,
            true,
            null,
            "Module capability allowed",
            List.of(),
            List.of()
        );
    }

    @Transactional(readOnly = true)
    public void requireCapability(Long storeId, String moduleKey) {
        evaluateCapability(storeId, moduleKey).requireAllowed();
    }

    private StoreModuleAccessEvaluation denied(
        Long storeId,
        String moduleKey,
        boolean moduleKnown,
        boolean persisted,
        boolean storeModuleEnabled,
        boolean environmentAvailable,
        String errorCode,
        String message,
        List<String> missingEnvironmentCapabilities,
        List<String> issueCodes
    ) {
        return new StoreModuleAccessEvaluation(
            storeId,
            moduleKey,
            moduleKnown,
            persisted,
            storeModuleEnabled,
            environmentAvailable,
            false,
            errorCode,
            message,
            List.copyOf(missingEnvironmentCapabilities),
            List.copyOf(issueCodes)
        );
    }

    private Map<String, StoreModule> indexModules(List<StoreModule> persistedModules) {
        Map<String, StoreModule> modulesByKey = new LinkedHashMap<>();
        for (StoreModule module : persistedModules == null ? List.<StoreModule>of() : persistedModules) {
            String moduleKey = module.module_key == null ? "" : module.module_key.trim().toUpperCase();
            if (moduleKey.isBlank()) {
                continue;
            }
            if (modulesByKey.put(moduleKey, module) != null) {
                throw new BusinessException("STORE_MODULE_DUPLICATE_PERSISTED: " + moduleKey);
            }
        }
        return modulesByKey;
    }

    private List<String> configurationIssues(String moduleKey, Map<String, StoreModule> modulesByKey) {
        List<String> issues = new ArrayList<>();
        for (ModuleDependencyRule rule : dependencyGraph.dependencies()) {
            Optional<ModuleDependencyType> type = ModuleDependencyType.from(rule.type());
            if (type.isEmpty() || !moduleKey.equals(rule.sourceModule())) {
                continue;
            }
            if (type.get() == ModuleDependencyType.REQUIRES) {
                StoreModule required = modulesByKey.get(rule.target());
                if (required == null) {
                    issues.add("REQUIRED_MODULE_MISSING:" + rule.target());
                } else if (!Boolean.TRUE.equals(required.enabled)) {
                    issues.add("REQUIRED_MODULE_DISABLED:" + rule.target());
                }
            } else if (type.get() == ModuleDependencyType.CONFLICTS_WITH) {
                StoreModule conflicting = modulesByKey.get(rule.target());
                if (conflicting != null && Boolean.TRUE.equals(conflicting.enabled)) {
                    issues.add("MODULE_CONFLICT:" + rule.target());
                }
            }
        }
        return issues;
    }

    private List<String> requiredEnvironmentCapabilities(String moduleKey) {
        return dependencyGraph.dependencies().stream()
            .filter(rule -> moduleKey.equals(rule.sourceModule()))
            .filter(rule -> ModuleDependencyType.REQUIRES_ENVIRONMENT_CAPABILITY.name().equals(rule.type()))
            .map(ModuleDependencyRule::target)
            .distinct()
            .toList();
    }

    private String normalizeModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new BusinessException("STORE_MODULE_KEY_REQUIRED");
        }
        return moduleKey.trim().toUpperCase();
    }
}
