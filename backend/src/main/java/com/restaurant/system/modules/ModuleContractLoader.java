package com.restaurant.system.modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

public class ModuleContractLoader {

    public static final String CATALOG_RESOURCE = "/module/module-catalog.v1.json";
    public static final String DEPENDENCY_GRAPH_RESOURCE = "/module/module-dependency-graph.v1.json";
    public static final String HARDWARE_CAPABILITY_CATALOG_RESOURCE = "/hardware/hardware-capability-catalog.v1.json";

    private final ObjectMapper objectMapper;

    public ModuleContractLoader() {
        this(new ObjectMapper());
    }

    public ModuleContractLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ModuleCatalogDefinition loadCatalog() {
        JsonNode root = readJson(CATALOG_RESOURCE);
        Set<String> moduleKeys = new LinkedHashSet<>();
        Set<String> coreModuleKeys = new LinkedHashSet<>();
        Map<String, ModuleState> defaultStates = new LinkedHashMap<>();
        Set<String> environmentCapabilities = new LinkedHashSet<>();
        HardwareCapabilityCatalogDefinition hardwareCatalog = loadHardwareCatalog();
        Set<String> hardwareCapabilities = new LinkedHashSet<>(hardwareCatalog.canonicalCapabilityKeys());
        List<ModuleDefinition> modules = new ArrayList<>();
        Map<String, ModuleDefinition> modulesByKey = new LinkedHashMap<>();

        for (JsonNode module : root.path("modules")) {
            String moduleKey = module.path("module_key").asText();
            ModuleState defaultState = ModuleState.fromDefaultState(module.path("default_state").asText());
            ModuleDefinition definition = new ModuleDefinition(
                moduleKey,
                module.path("display_name").asText(),
                module.path("classification").asText(),
                module.path("category").asText(),
                module.path("core").asBoolean(false),
                activeNormalStore(module, defaultState),
                module.path("activation_blocking").asBoolean(false),
                defaultState
            );
            moduleKeys.add(moduleKey);
            modules.add(definition);
            modulesByKey.put(moduleKey, definition);
            if (module.path("core").asBoolean(false)) {
                coreModuleKeys.add(moduleKey);
            }
            defaultStates.put(moduleKey, defaultState);
            module.path("required_environment_capabilities").forEach(capability ->
                environmentCapabilities.add(capability.asText())
            );
            module.path("required_hardware_capabilities").forEach(capability ->
                hardwareCapabilities.addAll(hardwareCatalog.canonicalKeys(capability.asText()))
            );
        }

        return new ModuleCatalogDefinition(
            root.path("catalog_version").asText(),
            List.copyOf(modules),
            Map.copyOf(modulesByKey),
            Set.copyOf(moduleKeys),
            Set.copyOf(coreModuleKeys),
            Map.copyOf(defaultStates),
            Set.copyOf(environmentCapabilities),
            Set.copyOf(hardwareCapabilities)
        );
    }

    public ModuleDependencyGraph loadDependencyGraph() {
        JsonNode root = readJson(DEPENDENCY_GRAPH_RESOURCE);
        List<ModuleDependencyRule> dependencies = StreamSupport.stream(root.path("dependencies").spliterator(), false)
            .map(node -> new ModuleDependencyRule(
                node.path("source_module").asText(),
                node.path("type").asText(),
                node.path("target").asText()
            ))
            .toList();
        return new ModuleDependencyGraph(
            root.path("graph_version").asText(),
            root.path("catalog_version").asText(),
            dependencies
        );
    }

    public HardwareCapabilityCatalogDefinition loadHardwareCatalog() {
        JsonNode root = readJson(HARDWARE_CAPABILITY_CATALOG_RESOURCE);
        List<HardwareCapabilityDefinition> capabilities = new ArrayList<>();
        Map<String, HardwareCapabilityDefinition> capabilitiesByKey = new LinkedHashMap<>();
        Map<String, Set<String>> canonicalKeysBySupportedKey = new LinkedHashMap<>();

        for (JsonNode node : root.path("capabilities")) {
            String capabilityKey = HardwareCapabilityCatalogDefinition.normalize(node.path("capability_key").asText());
            HardwareCapabilityDefinition definition = new HardwareCapabilityDefinition(
                capabilityKey,
                node.path("display_name").asText(),
                node.path("layer").asText(),
                node.path("category").asText(),
                node.path("readiness_contract").asText(),
                node.path("physical_binding").asBoolean(false),
                StreamSupport.stream(node.path("aliases").spliterator(), false)
                    .map(JsonNode::asText)
                    .map(HardwareCapabilityCatalogDefinition::normalize)
                    .toList()
            );
            capabilities.add(definition);
            capabilitiesByKey.put(capabilityKey, definition);
            canonicalKeysBySupportedKey
                .computeIfAbsent(capabilityKey, ignored -> new LinkedHashSet<>())
                .add(capabilityKey);
            for (String alias : definition.aliases()) {
                canonicalKeysBySupportedKey
                    .computeIfAbsent(alias, ignored -> new LinkedHashSet<>())
                    .add(capabilityKey);
            }
        }

        return new HardwareCapabilityCatalogDefinition(
            root.path("catalog_version").asText(),
            capabilities,
            capabilitiesByKey,
            canonicalKeysBySupportedKey
        );
    }

    private JsonNode readJson(String resourcePath) {
        try (InputStream stream = ModuleContractLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing module contract resource: " + resourcePath);
            }
            return objectMapper.readTree(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read module contract resource: " + resourcePath, exception);
        }
    }

    private boolean activeNormalStore(JsonNode module, ModuleState defaultState) {
        if (module.has("active_normal_store")) {
            return module.path("active_normal_store").asBoolean(false);
        }
        return module.path("core").asBoolean(false) && defaultState == ModuleState.ENABLED;
    }
}
