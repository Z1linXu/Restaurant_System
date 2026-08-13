package com.restaurant.system.modules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ModuleDependencyValidatorTest {

    private final ModuleContractLoader loader = new ModuleContractLoader();
    private final ModuleCatalogDefinition catalog = loader.loadCatalog();
    private final ModuleDependencyGraph graph = loader.loadDependencyGraph();
    private final ModuleDependencyValidator validator = new ModuleDependencyValidator(catalog, graph);

    @Test
    void graphDefinitionIsValidAgainstCatalog() {
        ModuleValidationResult result = validator.validateGraphDefinition();

        assertTrue(result.valid(), () -> "graph issues: " + result.issues());
    }

    @Test
    void graphCoversCatalogDeclaredDependenciesAndRequirements() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode catalogJson;
        JsonNode graphJson;
        try (InputStream catalogStream = ModuleDependencyValidatorTest.class.getResourceAsStream(
            ModuleContractLoader.CATALOG_RESOURCE
        );
             InputStream graphStream = ModuleDependencyValidatorTest.class.getResourceAsStream(
                 ModuleContractLoader.DEPENDENCY_GRAPH_RESOURCE
             )) {
            assertTrue(catalogStream != null, "catalog resource must exist");
            assertTrue(graphStream != null, "dependency graph resource must exist");
            catalogJson = objectMapper.readTree(catalogStream);
            graphJson = objectMapper.readTree(graphStream);
        }
        Set<String> graphRules = StreamSupport.stream(graphJson.path("dependencies").spliterator(), false)
            .map(rule -> ruleKey(
                rule.path("source_module").asText(),
                rule.path("type").asText(),
                rule.path("target").asText()
            ))
            .collect(Collectors.toUnmodifiableSet());

        for (JsonNode module : catalogJson.path("modules")) {
            String moduleKey = module.path("module_key").asText();
            for (JsonNode dependency : module.path("depends_on")) {
                assertTrue(
                    graphRules.contains(ruleKey(moduleKey, "REQUIRES", dependency.asText())),
                    moduleKey + " depends_on must be represented in A2 graph: " + dependency.asText()
                );
            }
            for (JsonNode capability : module.path("required_environment_capabilities")) {
                assertTrue(
                    graphRules.contains(ruleKey(moduleKey, "REQUIRES_ENVIRONMENT_CAPABILITY", capability.asText())),
                    moduleKey + " required environment capability must be represented in A2 graph: " + capability.asText()
                );
            }
            for (JsonNode capability : module.path("required_hardware_capabilities")) {
                assertTrue(
                    graphRules.contains(ruleKey(moduleKey, "REQUIRES_HARDWARE_CAPABILITY", capability.asText())),
                    moduleKey + " required hardware capability must be represented in A2 graph: " + capability.asText()
                );
            }
        }
    }

    @Test
    void defaultNormalStoreConfigurationIsValid() {
        ModuleValidationResult result = validator.validate(ModuleConfigurationInput.defaultsWith(
            normalStoreEnvironmentCapabilities(),
            normalStoreHardwareCapabilities()
        ));

        assertTrue(result.valid(), () -> "validation issues: " + result.issues());
    }

    @Test
    void kdsOffIsValidWithoutKdsRuntimeOrDisplayHardware() {
        ModuleValidationResult result = validator.validate(new ModuleConfigurationInput(
            Map.of("KDS", ModuleState.DISABLED),
            normalStoreEnvironmentCapabilities(),
            normalStoreHardwareCapabilities()
        ));

        assertTrue(result.valid(), () -> "KDS OFF must be valid: " + result.issues());
    }

    @Test
    void unknownModuleFailsClosed() {
        ModuleValidationResult result = validator.validate(new ModuleConfigurationInput(
            Map.of("NOT_A_REAL_MODULE", ModuleState.ENABLED),
            normalStoreEnvironmentCapabilities(),
            normalStoreHardwareCapabilities()
        ));

        assertFalse(result.valid());
        assertTrue(result.issueCodes().contains(ModuleValidationCode.UNKNOWN_MODULE));
    }

    @Test
    void requiredModuleDisabledFailsClosed() {
        ModuleValidationResult result = validator.validate(new ModuleConfigurationInput(
            Map.of("MENU", ModuleState.DISABLED),
            normalStoreEnvironmentCapabilities(),
            normalStoreHardwareCapabilities()
        ));

        assertFalse(result.valid());
        assertTrue(result.issueCodes().contains(ModuleValidationCode.CORE_MODULE_DISABLED));
        assertTrue(result.issueCodes().contains(ModuleValidationCode.REQUIRED_MODULE_DISABLED));
    }

    @Test
    void enabledModuleMissingEnvironmentCapabilityFailsClosed() {
        ModuleValidationResult result = validator.validate(ModuleConfigurationInput.defaultsWith(
            without(normalStoreEnvironmentCapabilities(), "PRINTING_FEATURE_FLAG"),
            normalStoreHardwareCapabilities()
        ));

        assertFalse(result.valid());
        assertTrue(result.issueCodes().contains(ModuleValidationCode.ENVIRONMENT_CAPABILITY_MISSING));
    }

    @Test
    void enabledModuleMissingHardwareCapabilityFailsClosed() {
        ModuleValidationResult result = validator.validate(ModuleConfigurationInput.defaultsWith(
            normalStoreEnvironmentCapabilities(),
            without(normalStoreHardwareCapabilities(), "PRINTER_TOPOLOGY_FOR_REAL_OR_PAD_DIRECT")
        ));

        assertFalse(result.valid());
        assertTrue(result.issueCodes().contains(ModuleValidationCode.HARDWARE_CAPABILITY_MISSING));
    }

    @Test
    void conflictRelationshipFailsClosedWhenBothModulesAreEnabled() {
        List<ModuleDependencyRule> dependencies = new ArrayList<>(graph.dependencies());
        dependencies.add(new ModuleDependencyRule("KDS", "CONFLICTS_WITH", "ANALYTICS_ADVANCED"));
        ModuleDependencyValidator validatorWithConflict = new ModuleDependencyValidator(
            catalog,
            new ModuleDependencyGraph("TEST_GRAPH", graph.catalogVersion(), dependencies)
        );
        ModuleValidationResult result = validatorWithConflict.validate(new ModuleConfigurationInput(
            Map.of(
                "KDS", ModuleState.ENABLED,
                "ANALYTICS_ADVANCED", ModuleState.ENABLED
            ),
            allEnvironmentCapabilities(),
            allHardwareCapabilities()
        ));

        assertFalse(result.valid());
        assertTrue(result.issueCodes().contains(ModuleValidationCode.MODULE_CONFLICT));
    }

    @Test
    void invalidDependencyGraphFailsClosed() {
        ModuleDependencyValidator invalidValidator = new ModuleDependencyValidator(
            catalog,
            new ModuleDependencyGraph(
                "INVALID_TEST_GRAPH",
                graph.catalogVersion(),
                List.of(new ModuleDependencyRule("MISSING_SOURCE", "REQUIRES", "MENU"))
            )
        );

        ModuleValidationResult result = invalidValidator.validate(ModuleConfigurationInput.defaultsWith(
            normalStoreEnvironmentCapabilities(),
            normalStoreHardwareCapabilities()
        ));

        assertFalse(result.valid());
        assertTrue(result.issueCodes().contains(ModuleValidationCode.INVALID_DEPENDENCY_GRAPH));
    }

    @Test
    void machineReadableOutcomesReferenceStableIssueCodes() throws Exception {
        JsonNode graphJson;
        try (InputStream stream = ModuleDependencyValidatorTest.class.getResourceAsStream(
            ModuleContractLoader.DEPENDENCY_GRAPH_RESOURCE
        )) {
            assertTrue(stream != null, "dependency graph resource must exist");
            graphJson = new ObjectMapper().readTree(stream);
        }
        Set<String> stableCodes = Arrays.stream(ModuleValidationCode.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

        for (JsonNode outcome : graphJson.path("machine_readable_outcomes")) {
            assertFalse(outcome.path("scenario").asText().isBlank(), "scenario id is required");
            assertTrue(outcome.has("expected_valid"), outcome.path("scenario").asText() + " expected_valid is required");
            for (JsonNode expectedCode : outcome.path("expected_issue_codes")) {
                assertTrue(
                    stableCodes.contains(expectedCode.asText()),
                    outcome.path("scenario").asText() + " has unknown issue code " + expectedCode.asText()
                );
            }
        }
    }

    private Set<String> normalStoreEnvironmentCapabilities() {
        return Set.of(
            "CORE_POS_RUNTIME",
            "AUTH_RUNTIME",
            "DATABASE",
            "WEBSOCKET_RUNTIME",
            "ADMIN_RUNTIME",
            "PRINTING_FEATURE_FLAG",
            "PRINT_MODE_RUNTIME",
            "ANALYTICS_FEATURE_FLAG"
        );
    }

    private Set<String> normalStoreHardwareCapabilities() {
        return Set.of(
            "TOUCH_CLIENT",
            "PRINTER_TOPOLOGY_FOR_REAL_OR_PAD_DIRECT",
            "PAD_DEVICE_FOR_PAD_DIRECT"
        );
    }

    private Set<String> allEnvironmentCapabilities() {
        return catalog.environmentCapabilities();
    }

    private Set<String> allHardwareCapabilities() {
        return catalog.hardwareCapabilities();
    }

    private Set<String> without(Set<String> values, String valueToRemove) {
        return values.stream()
            .filter(value -> !value.equals(valueToRemove))
            .collect(Collectors.toUnmodifiableSet());
    }

    private String ruleKey(String sourceModule, String type, String target) {
        return sourceModule + "|" + type + "|" + target;
    }
}
