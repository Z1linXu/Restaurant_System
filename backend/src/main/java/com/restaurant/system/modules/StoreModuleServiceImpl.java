package com.restaurant.system.modules;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.modules.dto.StoreModuleConfigurationResponse;
import com.restaurant.system.modules.dto.StoreModuleResponse;
import com.restaurant.system.modules.dto.StoreModuleUpdateRequest;
import com.restaurant.system.modules.dto.StoreModuleValidationIssueResponse;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreModuleServiceImpl implements StoreModuleService {

    private static final String SOURCE_ADMIN_OVERRIDE = "ADMIN_OVERRIDE";
    private static final String CONFIGURED = "CONFIGURED";
    private static final String PRINTING = "PRINTING";

    private final StoreRepository storeRepository;
    private final StoreModuleRepository storeModuleRepository;
    private final StoreModuleCapabilityProvider capabilityProvider;
    private final ModuleCatalogDefinition catalog;
    private final ModuleDependencyGraph dependencyGraph;
    private final ModuleDependencyValidator validator;

    @Autowired
    public StoreModuleServiceImpl(
        StoreRepository storeRepository,
        StoreModuleRepository storeModuleRepository,
        StoreModuleCapabilityProvider capabilityProvider
    ) {
        this(
            storeRepository,
            storeModuleRepository,
            capabilityProvider,
            new ModuleContractLoader()
        );
    }

    StoreModuleServiceImpl(
        StoreRepository storeRepository,
        StoreModuleRepository storeModuleRepository,
        StoreModuleCapabilityProvider capabilityProvider,
        ModuleContractLoader loader
    ) {
        this.storeRepository = storeRepository;
        this.storeModuleRepository = storeModuleRepository;
        this.capabilityProvider = capabilityProvider;
        this.catalog = loader.loadCatalog();
        this.dependencyGraph = loader.loadDependencyGraph();
        this.validator = new ModuleDependencyValidator(catalog, dependencyGraph);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreModuleConfigurationResponse getConfiguration(Long storeId) {
        Store store = requireStore(storeId);
        List<StoreModule> persistedModules = storeModuleRepository.findAllByStoreIdOrderByIdAsc(storeId);
        return buildResponse(store, persistedModules);
    }

    @Override
    @Transactional
    public StoreModuleConfigurationResponse updateConfiguration(Long storeId, StoreModuleUpdateRequest request) {
        Store store = requireStore(storeId);
        if (request == null || request.modules == null) {
            throw new BusinessException("STORE_MODULE_REQUEST_REQUIRED");
        }
        if (request.store_id != null && !request.store_id.equals(storeId)) {
            throw new BusinessException("STORE_MODULE_STORE_MISMATCH");
        }

        List<StoreModule> persistedModules = storeModuleRepository.findAllByStoreIdOrderByIdAsc(storeId);
        Map<String, StoreModule> modulesByKey = indexPersistedModules(persistedModules);
        requireCompletePersistedConfiguration(modulesByKey);

        Set<String> seen = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (StoreModuleUpdateRequest.ModuleUpdate update : request.modules) {
            String moduleKey = normalizeModuleKey(update == null ? null : update.module_key);
            if (!seen.add(moduleKey)) {
                throw new BusinessException("STORE_MODULE_DUPLICATE_UPDATE: " + moduleKey);
            }
            if (!catalog.hasModule(moduleKey)) {
                throw new BusinessException("STORE_MODULE_UNKNOWN: " + moduleKey);
            }
            if (update.enabled == null) {
                throw new BusinessException("STORE_MODULE_ENABLED_REQUIRED: " + moduleKey);
            }
            ModuleDefinition definition = catalog.module(moduleKey);
            if (definition.core() && !Boolean.TRUE.equals(update.enabled) && isActiveStore(store)) {
                throw new BusinessException("CORE_MODULE_CANNOT_BE_DISABLED: " + moduleKey);
            }
            StoreModule storeModule = modulesByKey.get(moduleKey);
            if (storeModule == null) {
                throw new BusinessException("STORE_MODULE_CONFIGURATION_INCOMPLETE: " + moduleKey);
            }
            storeModule.enabled = update.enabled;
            storeModule.source = SOURCE_ADMIN_OVERRIDE;
            storeModule.configuration_status = CONFIGURED;
            storeModule.updated_at = now;
        }

        List<StoreModule> savedModules = storeModuleRepository.saveAll(modulesByKey.values());
        StoreModuleConfigurationResponse response = buildResponse(store, savedModules);
        if (!Boolean.TRUE.equals(response.valid)) {
            String issueCodes = response.validation_issues.stream()
                .map(issue -> issue.code)
                .distinct()
                .collect(Collectors.joining(","));
            throw new BusinessException("STORE_MODULE_CONFIGURATION_INVALID: " + issueCodes);
        }
        return response;
    }

    private Store requireStore(Long storeId) {
        if (storeId == null) {
            throw new BusinessException("STORE_ID_REQUIRED");
        }
        return storeRepository.findById(storeId)
            .orElseThrow(() -> new BusinessException("STORE_NOT_FOUND"));
    }

    private StoreModuleConfigurationResponse buildResponse(Store store, List<StoreModule> persistedModules) {
        Map<String, StoreModule> modulesByKey = indexPersistedModules(persistedModules);
        Set<String> environmentCapabilities = capabilityProvider.environmentCapabilities(store.id);
        Set<String> hardwareCapabilities = capabilityProvider.hardwareCapabilities(store.id);
        List<StoreModuleValidationIssueResponse> validationIssues = new ArrayList<>();
        validationIssues.addAll(persistenceIssues(modulesByKey));

        ModuleValidationResult validationResult = validator.validate(new ModuleConfigurationInput(
            moduleStates(modulesByKey),
            environmentCapabilities,
            hardwareCapabilities
        ));
        validationResult.issues().stream()
            .map(this::toIssueResponse)
            .forEach(validationIssues::add);

        StoreModuleConfigurationResponse response = new StoreModuleConfigurationResponse();
        response.store_id = store.id;
        response.catalog_version = catalog.catalogVersion();
        response.dependency_graph_version = dependencyGraph.graphVersion();
        response.valid = validationIssues.isEmpty();
        response.validation_status = Boolean.TRUE.equals(response.valid) ? "VALID" : "INVALID";
        response.environment_capability_source = "RUNTIME_FEATURE_FLAGS_AND_SHARED_INFRASTRUCTURE";
        response.hardware_capability_source = "STORE_RUNTIME_TOPOLOGY_WITH_PAD_DIRECT_CONDITIONAL_READINESS";
        response.legacy_compatibility_status = "A3_FOUNDATION_ONLY_LEGACY_RUNTIME_GATING_RETAINED_UNTIL_A6_A7";
        response.legacy_precedence = "CURRENT_RUNTIME_BEHAVIOR_STILL_USES_EXISTING_ENVIRONMENT_FLAGS_AND_STORE_RUNTIME_FIELDS; STORE_MODULES_ARE_CANONICAL_STATE_FOR_A3_READ_CONTRACT";
        response.environment_capabilities = sorted(environmentCapabilities);
        response.hardware_capabilities = sorted(hardwareCapabilities);
        response.modules = catalog.modules().stream()
            .map(definition -> toModuleResponse(definition, modulesByKey.get(definition.moduleKey()), store))
            .toList();
        response.validation_issues = validationIssues;
        return response;
    }

    private Map<String, StoreModule> indexPersistedModules(List<StoreModule> persistedModules) {
        Map<String, StoreModule> modulesByKey = new LinkedHashMap<>();
        for (StoreModule module : persistedModules == null ? List.<StoreModule>of() : persistedModules) {
            String moduleKey = normalizeModuleKey(module.module_key);
            if (!catalog.hasModule(moduleKey)) {
                modulesByKey.putIfAbsent(moduleKey, module);
                continue;
            }
            if (modulesByKey.put(moduleKey, module) != null) {
                throw new BusinessException("STORE_MODULE_DUPLICATE_PERSISTED: " + moduleKey);
            }
        }
        return modulesByKey;
    }

    private void requireCompletePersistedConfiguration(Map<String, StoreModule> modulesByKey) {
        for (ModuleDefinition definition : catalog.modules()) {
            if (!modulesByKey.containsKey(definition.moduleKey())) {
                throw new BusinessException("STORE_MODULE_CONFIGURATION_INCOMPLETE: " + definition.moduleKey());
            }
        }
    }

    private List<StoreModuleValidationIssueResponse> persistenceIssues(Map<String, StoreModule> modulesByKey) {
        List<StoreModuleValidationIssueResponse> issues = new ArrayList<>();
        for (String moduleKey : modulesByKey.keySet()) {
            if (!catalog.hasModule(moduleKey)) {
                issues.add(issue("STORE_MODULE_UNKNOWN", moduleKey, null, "Unknown persisted Store module"));
            }
        }
        for (ModuleDefinition definition : catalog.modules()) {
            if (!modulesByKey.containsKey(definition.moduleKey())) {
                issues.add(issue(
                    "STORE_MODULE_MISSING",
                    definition.moduleKey(),
                    null,
                    "Store module persistence is incomplete"
                ));
            }
        }
        return issues;
    }

    private Map<String, ModuleState> moduleStates(Map<String, StoreModule> modulesByKey) {
        Map<String, ModuleState> states = new LinkedHashMap<>();
        for (ModuleDefinition definition : catalog.modules()) {
            StoreModule persisted = modulesByKey.get(definition.moduleKey());
            if (persisted != null && Boolean.TRUE.equals(persisted.enabled)) {
                states.put(definition.moduleKey(), ModuleState.ENABLED);
            } else if (persisted != null) {
                states.put(definition.moduleKey(), ModuleState.DISABLED);
            }
        }
        for (String moduleKey : modulesByKey.keySet()) {
            if (!catalog.hasModule(moduleKey)) {
                states.put(moduleKey, ModuleState.ENABLED);
            }
        }
        return states;
    }

    private StoreModuleResponse toModuleResponse(
        ModuleDefinition definition,
        StoreModule persisted,
        Store store
    ) {
        StoreModuleResponse response = new StoreModuleResponse();
        response.module_key = definition.moduleKey();
        response.display_name = definition.displayName();
        response.classification = definition.classification();
        response.category = definition.category();
        response.enabled = persisted == null ? null : Boolean.TRUE.equals(persisted.enabled);
        response.default_enabled = definition.defaultState() == ModuleState.ENABLED;
        response.core_required = definition.core();
        response.active_normal_store_required = definition.activeNormalStore();
        response.activation_blocking = definition.activationBlocking();
        response.persisted = persisted != null;
        response.source = persisted == null ? null : persisted.source;
        response.configuration_status = persisted == null ? "MISSING" : persisted.configuration_status;
        response.profile_code = persisted == null ? null : persisted.profile_code;
        response.profile_version = persisted == null ? null : persisted.profile_version;
        if (PRINTING.equals(definition.moduleKey())) {
            response.legacy_runtime_mode = store.printing_mode;
            response.legacy_store_flag = store.printing_enabled;
        }
        return response;
    }

    private StoreModuleValidationIssueResponse toIssueResponse(ModuleValidationIssue issue) {
        return issue(
            issue.code().name(),
            issue.moduleKey(),
            issue.target(),
            issue.message()
        );
    }

    private StoreModuleValidationIssueResponse issue(String code, String moduleKey, String target, String message) {
        StoreModuleValidationIssueResponse response = new StoreModuleValidationIssueResponse();
        response.code = code;
        response.module_key = moduleKey;
        response.target = target;
        response.message = message;
        return response;
    }

    private String normalizeModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new BusinessException("STORE_MODULE_KEY_REQUIRED");
        }
        return moduleKey.trim().toUpperCase();
    }

    private boolean isActiveStore(Store store) {
        return store.status == null || "active".equalsIgnoreCase(store.status);
    }

    private List<String> sorted(Set<String> values) {
        return values.stream()
            .sorted(Comparator.naturalOrder())
            .toList();
    }
}
