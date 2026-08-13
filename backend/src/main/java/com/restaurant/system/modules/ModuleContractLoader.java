package com.restaurant.system.modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

public class ModuleContractLoader {

    public static final String CATALOG_RESOURCE = "/module/module-catalog.v1.json";
    public static final String DEPENDENCY_GRAPH_RESOURCE = "/module/module-dependency-graph.v1.json";

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
        Set<String> hardwareCapabilities = new LinkedHashSet<>();

        for (JsonNode module : root.path("modules")) {
            String moduleKey = module.path("module_key").asText();
            moduleKeys.add(moduleKey);
            if (module.path("core").asBoolean(false)) {
                coreModuleKeys.add(moduleKey);
            }
            defaultStates.put(moduleKey, ModuleState.fromDefaultState(module.path("default_state").asText()));
            module.path("required_environment_capabilities").forEach(capability ->
                environmentCapabilities.add(capability.asText())
            );
            module.path("required_hardware_capabilities").forEach(capability ->
                hardwareCapabilities.add(capability.asText())
            );
        }

        return new ModuleCatalogDefinition(
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
}
