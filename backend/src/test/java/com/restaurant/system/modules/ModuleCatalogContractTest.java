package com.restaurant.system.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ModuleCatalogContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> EXPECTED_CLASSIFICATIONS = Set.of(
        "CORE_MODULE",
        "OPTIONAL_MODULE",
        "MODULE_CAPABILITY",
        "STORE_CONFIGURATION",
        "ENVIRONMENT_CAPABILITY",
        "HARDWARE_CAPABILITY",
        "ROLE_CAPABILITY",
        "SHARED_INFRASTRUCTURE",
        "LEGACY_COUPLING",
        "LEGACY_FLAG",
        "RUNTIME_MODE",
        "NOT_A_MODULE"
    );

    @Test
    void moduleKeysAreUniqueAndReferencesAreValid() throws Exception {
        JsonNode catalog = loadCatalog();
        JsonNode modules = catalog.path("modules");
        assertTrue(modules.isArray(), "modules must be an array");

        Set<String> moduleKeys = new HashSet<>();
        for (JsonNode module : modules) {
            String moduleKey = requiredText(module, "module_key");
            assertTrue(moduleKeys.add(moduleKey), "duplicate module_key: " + moduleKey);
            assertFalse(requiredText(module, "display_name").isBlank(), moduleKey + " display_name is required");
            assertFalse(requiredText(module, "category").isBlank(), moduleKey + " category is required");
            assertFalse(requiredText(module, "classification").isBlank(), moduleKey + " classification is required");
            assertTrue(module.has("core"), moduleKey + " core is required");
            assertTrue(module.has("optional"), moduleKey + " optional is required");
            assertFalse(
                module.path("core").asBoolean() && module.path("optional").asBoolean(),
                moduleKey + " cannot be both core and optional"
            );
            assertFalse(requiredText(module, "default_state").isBlank(), moduleKey + " default_state is required");
            assertTrue(module.has("activation_blocking"), moduleKey + " activation_blocking is required");
            assertTrue(module.path("capabilities").isArray(), moduleKey + " capabilities must be an array");
            assertTrue(module.path("depends_on").isArray(), moduleKey + " depends_on must be an array");
            assertTrue(module.path("conflicts_with").isArray(), moduleKey + " conflicts_with must be an array");
            assertTrue(module.path("frontend_routes").isArray(), moduleKey + " frontend_routes must be an array");
            assertTrue(module.path("backend_api_prefixes").isArray(), moduleKey + " backend_api_prefixes must be an array");
            assertTrue(module.path("required_permissions").isArray(), moduleKey + " required_permissions must be an array");
        }

        for (JsonNode module : modules) {
            String moduleKey = requiredText(module, "module_key");
            assertReferencesExist(moduleKey, "depends_on", module.path("depends_on"), moduleKeys);
            assertReferencesExist(moduleKey, "conflicts_with", module.path("conflicts_with"), moduleKeys);
        }
    }

    @Test
    void ownerRequiredNormalStoreCapabilitiesAreExpressed() throws Exception {
        JsonNode catalog = loadCatalog();
        Set<String> moduleKeys = moduleKeys(catalog);
        for (String requiredModule : Set.of(
            "ORDERING_POS",
            "MENU",
            "MENU_MANAGEMENT",
            "TABLE_MANAGEMENT",
            "PRINTING",
            "ORDER_HISTORY",
            "REPORTING_CORE"
        )) {
            assertTrue(moduleKeys.contains(requiredModule), "missing required module " + requiredModule);
            JsonNode module = moduleByKey(catalog, requiredModule);
            assertTrue(module.path("core").asBoolean(), requiredModule + " must be core");
            assertEquals("ENABLED", requiredText(module, "default_state"), requiredModule + " must default enabled");
            assertTrue(module.path("activation_blocking").asBoolean(), requiredModule + " must block active readiness");
        }

        JsonNode printing = moduleByKey(catalog, "PRINTING");
        assertCapabilityCovered(catalog, "ORDERING");
        assertCapabilityCovered(catalog, "POS");
        assertCapabilityCovered(catalog, "MENU");
        assertCapabilityCovered(catalog, "MENU_MANAGEMENT");
        assertCapabilityCovered(catalog, "TABLE_MANAGEMENT");
        assertCapabilityCovered(catalog, "PRINTING");
        assertArrayContains(printing.path("capabilities"), "GRAB_PRINTING");
        assertArrayContains(printing.path("capabilities"), "FRONTDESK_RECEIPT");
        assertCapabilityCovered(catalog, "ORDER_HISTORY");
        assertCapabilityCovered(catalog, "REPORTING");
    }

    @Test
    void kdsIsOptionalDisabledByDefaultAndNotActivationBlocking() throws Exception {
        JsonNode kds = moduleByKey(loadCatalog(), "KDS");
        assertEquals("OPTIONAL_MODULE", requiredText(kds, "classification"));
        assertFalse(kds.path("core").asBoolean(), "KDS must not be core");
        assertEquals("DISABLED", requiredText(kds, "default_state"));
        assertFalse(kds.path("activation_blocking").asBoolean(), "KDS off must be a valid active-store configuration");
    }

    @Test
    void featureFlagsAreClassifiedAndPointOnlyToKnownModulesWhenPresent() throws Exception {
        JsonNode catalog = loadCatalog();
        Set<String> moduleKeys = moduleKeys(catalog);
        Set<String> classifications = classifications(catalog);
        JsonNode flags = catalog.path("current_feature_flag_classification");
        assertTrue(flags.isArray(), "current_feature_flag_classification must be an array");

        Set<String> observedFlags = new HashSet<>();
        for (JsonNode flag : flags) {
            observedFlags.add(requiredText(flag, "flag"));
            String classification = requiredText(flag, "classification");
            assertTrue(
                classifications.contains(classification),
                "flag classification must be in catalog vocabulary: " + classification
            );
            JsonNode target = flag.path("target_module");
            if (!target.isMissingNode() && !target.isNull()) {
                assertTrue(moduleKeys.contains(target.asText()), "unknown target_module " + target.asText());
            }
        }

        assertTrue(observedFlags.contains("app.features.core-pos"));
        assertTrue(observedFlags.contains("app.features.printing"));
        assertTrue(observedFlags.contains("app.features.kds"));
        assertTrue(observedFlags.contains("app.features.admin"));
        assertTrue(observedFlags.contains("app.features.analytics"));
        assertTrue(observedFlags.contains("stores.printing_enabled"));
        assertTrue(observedFlags.contains("stores.printing_mode"));
        assertTrue(observedFlags.contains("app.printing.allowed-modes"));
        assertTrue(observedFlags.contains("frontend.featureConfig.ts"));
        assertTrue(observedFlags.contains("VITE_ENABLE_DEV_ROLE_SWITCHER"));
        assertTrue(observedFlags.contains("VITE_NETWORK_DIAGNOSTICS_ENABLED"));
    }

    @Test
    void routeAndApiMappingsAreNotOrphaned() throws Exception {
        JsonNode catalog = loadCatalog();
        for (JsonNode module : catalog.path("modules")) {
            String moduleKey = requiredText(module, "module_key");
            boolean hasFrontendRoute = module.path("frontend_routes").size() > 0;
            boolean hasBackendApi = module.path("backend_api_prefixes").size() > 0;
            assertTrue(hasFrontendRoute || hasBackendApi, moduleKey + " must map to at least one route or API prefix");
        }
    }

    @Test
    void classificationsUseApprovedVocabulary() throws Exception {
        JsonNode catalog = loadCatalog();
        Set<String> classifications = classifications(catalog);
        assertEquals(EXPECTED_CLASSIFICATIONS, classifications, "classification vocabulary drifted");

        for (JsonNode module : catalog.path("modules")) {
            String moduleKey = requiredText(module, "module_key");
            String classification = requiredText(module, "classification");
            assertTrue(
                classifications.contains(classification),
                moduleKey + " classification must be in catalog vocabulary: " + classification
            );
        }

        JsonNode capabilityClassifications = catalog.path("capability_classification");
        assertTrue(capabilityClassifications.isObject(), "capability_classification must be an object");
        Iterator<JsonNode> values = capabilityClassifications.elements();
        while (values.hasNext()) {
            String classification = values.next().asText();
            assertTrue(
                classifications.contains(classification),
                "capability classification must be in catalog vocabulary: " + classification
            );
        }
    }

    private static JsonNode loadCatalog() throws Exception {
        try (InputStream stream = ModuleCatalogContractTest.class.getResourceAsStream("/module/module-catalog.v1.json")) {
            assertNotNull(stream, "module-catalog.v1.json resource must exist");
            return OBJECT_MAPPER.readTree(stream);
        }
    }

    private static String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        assertFalse(value.isMissingNode() || value.isNull(), fieldName + " is required");
        return value.asText();
    }

    private static Set<String> moduleKeys(JsonNode catalog) {
        return StreamSupport.stream(catalog.path("modules").spliterator(), false)
            .map(module -> requiredText(module, "module_key"))
            .collect(Collectors.toSet());
    }

    private static Set<String> classifications(JsonNode catalog) {
        return StreamSupport.stream(catalog.path("classifications").spliterator(), false)
            .map(JsonNode::asText)
            .collect(Collectors.toSet());
    }

    private static JsonNode moduleByKey(JsonNode catalog, String moduleKey) {
        for (JsonNode module : catalog.path("modules")) {
            if (moduleKey.equals(module.path("module_key").asText())) {
                return module;
            }
        }
        throw new AssertionError("missing module " + moduleKey);
    }

    private static void assertReferencesExist(String moduleKey, String fieldName, JsonNode values, Set<String> moduleKeys) {
        Iterator<JsonNode> iterator = values.elements();
        while (iterator.hasNext()) {
            String reference = iterator.next().asText();
            assertTrue(moduleKeys.contains(reference), moduleKey + " " + fieldName + " references unknown module " + reference);
        }
    }

    private static void assertArrayContains(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.asText())) {
                return;
            }
        }
        throw new AssertionError("missing array value " + expected);
    }

    private static void assertCapabilityCovered(JsonNode catalog, String expected) {
        for (JsonNode module : catalog.path("modules")) {
            for (JsonNode capability : module.path("capabilities")) {
                if (expected.equals(capability.asText())) {
                    return;
                }
            }
        }
        throw new AssertionError("missing owner-required capability " + expected);
    }
}
