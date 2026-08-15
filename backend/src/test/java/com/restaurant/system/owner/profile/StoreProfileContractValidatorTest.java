package com.restaurant.system.owner.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoreProfileContractValidatorTest {

    private StoreProfileContractValidator validator;
    private String contentJson;
    private List<StoreProfileArtifactInput> artifacts;

    @BeforeEach
    void setUp() {
        validator = new StoreProfileContractValidator();
        contentJson = """
            {
              "profile_code": "TEST_PROFILE",
              "profile_version": "v1",
              "schema_version": "STORE_PROFILE_CONTRACT_V1",
              "module_defaults": {
                "environment_capabilities": [
                  "CORE_POS_RUNTIME",
                  "AUTH_RUNTIME",
                  "DATABASE",
                  "WEBSOCKET_RUNTIME",
                  "ADMIN_RUNTIME",
                  "PRINTING_FEATURE_FLAG",
                  "PRINT_MODE_RUNTIME",
                  "ANALYTICS_FEATURE_FLAG"
                ],
                "hardware_capabilities": [
                  "TOUCH_CLIENT",
                  "PRINTER_TOPOLOGY_FOR_REAL_OR_PAD_DIRECT",
                  "PAD_DEVICE_FOR_PAD_DIRECT"
                ],
                "modules": [
                  {"module_key": "ORDERING_POS", "enabled": true},
                  {"module_key": "MENU", "enabled": true},
                  {"module_key": "MENU_MANAGEMENT", "enabled": true},
                  {"module_key": "TABLE_MANAGEMENT", "enabled": true},
                  {"module_key": "PRINTING", "enabled": true},
                  {"module_key": "ORDER_HISTORY", "enabled": true},
                  {"module_key": "REPORTING_CORE", "enabled": true},
                  {"module_key": "STAFF_ACCESS", "enabled": true},
                  {"module_key": "STORE_ADMINISTRATION", "enabled": true},
                  {"module_key": "KDS", "enabled": false},
                  {"module_key": "ANALYTICS_ADVANCED", "enabled": false}
                ]
              },
              "template_references": {
                "menu_template": {"artifact_code": "MENU_TEMPLATE", "artifact_version": "v1", "fingerprint_sha256": "%s"},
                "pricing_policy": {"artifact_code": "PRICING_POLICY", "artifact_version": "v1", "fingerprint_sha256": "%s"},
                "combo_configuration": {"artifact_code": "COMBO_CONFIGURATION", "artifact_version": "v1", "fingerprint_sha256": "%s"},
                "tables": {"artifact_code": "TABLE_TEMPLATE", "artifact_version": "v1", "fingerprint_sha256": "%s"},
                "stations": {"artifact_code": "STATION_TEMPLATE", "artifact_version": "v1", "fingerprint_sha256": "%s"},
                "logical_printing_topology": {"artifact_code": "PRINTING_TOPOLOGY", "artifact_version": "v1", "fingerprint_sha256": "%s"},
                "printing_display_rules": {"artifact_code": "PRINTING_DISPLAY_RULES", "artifact_version": "v1", "fingerprint_sha256": "%s"},
                "role_access_defaults": {"artifact_code": "ROLE_ACCESS_DEFAULTS", "artifact_version": "v1", "fingerprint_sha256": "%s"},
                "hardware_requirements": {"artifact_code": "HARDWARE_REQUIREMENTS", "artifact_version": "v1", "fingerprint_sha256": "%s"}
              },
              "materialization_contract": {
                "uses_profile_local_refs": true,
                "new_surrogate_ids_required": true,
                "source_store_db_ids_allowed": false
              }
            }
            """.formatted(
                hash("{}"), hash("{}"), hash("{}"), hash("{}"),
                hash("{}"), hash("{}"), hash("{}"), hash("{}"),
                hash("{}")
            );
        artifacts = List.of(
            artifact("MODULE_DEFAULTS", "MODULE_DEFAULTS", "{}"),
            artifact("MENU_TEMPLATE", "MENU_TEMPLATE", "{}"),
            artifact("PRICING_POLICY", "PRICING_POLICY", "{}"),
            artifact("COMBO_CONFIGURATION", "COMBO_CONFIGURATION", "{}"),
            artifact("TABLE_TEMPLATE", "TABLE_TEMPLATE", "{}"),
            artifact("STATION_TEMPLATE", "STATION_TEMPLATE", "{}"),
            artifact("LOGICAL_PRINTING_TOPOLOGY", "PRINTING_TOPOLOGY", "{}"),
            artifact("PRINTING_DISPLAY_RULES", "PRINTING_DISPLAY_RULES", "{}"),
            artifact("ROLE_ACCESS_DEFAULTS", "ROLE_ACCESS_DEFAULTS", "{}"),
            artifact("HARDWARE_REQUIREMENTS", "HARDWARE_REQUIREMENTS", "{}")
        );
    }

    @Test
    void acceptsASecretFreeVersionedProfileContract() {
        String fingerprint = validator.computeAggregateFingerprint(
            "TEST_PROFILE", "v1", StoreProfileContractValidator.SCHEMA_VERSION, contentJson, artifacts
        );

        StoreProfileValidationResult result = validator.validate(
            "TEST_PROFILE",
            "v1",
            StoreProfileContractValidator.SCHEMA_VERSION,
            contentJson,
            fingerprint,
            artifacts
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.computedFingerprint()).isEqualTo(fingerprint);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void deterministicFingerprintIgnoresJsonObjectFieldOrderButChangesBusinessContent() {
        String first = "{\"b\":2,\"a\":1}";
        String reordered = "{\"a\":1,\"b\":2}";
        String changed = "{\"a\":1,\"b\":3}";

        assertThat(StoreProfileCanonicalJson.sha256Canonical(first))
            .isEqualTo(StoreProfileCanonicalJson.sha256Canonical(reordered));
        assertThat(StoreProfileCanonicalJson.sha256Canonical(first))
            .isNotEqualTo(StoreProfileCanonicalJson.sha256Canonical(changed));
    }

    @Test
    void rejectsUnknownModulesAndDisabledCoreModulesThroughA2Validator() {
        String invalid = contentJson
            .replace("{\"module_key\": \"MENU\", \"enabled\": true}", "{\"module_key\": \"MENU\", \"enabled\": false}")
            .replace("{\"module_key\": \"KDS\", \"enabled\": false}", "{\"module_key\": \"NOT_A_MODULE\", \"enabled\": true}");
        String fingerprint = validator.computeAggregateFingerprint(
            "TEST_PROFILE", "v1", StoreProfileContractValidator.SCHEMA_VERSION, invalid, artifacts
        );

        StoreProfileValidationResult result = validator.validate(
            "TEST_PROFILE",
            "v1",
            StoreProfileContractValidator.SCHEMA_VERSION,
            invalid,
            fingerprint,
            artifacts
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(StoreProfileValidationIssue::code)
            .contains("MODULE_VALIDATION_UNKNOWN_MODULE", "MODULE_VALIDATION_CORE_MODULE_DISABLED");
    }

    @Test
    void postA11ProfileVersionsRequirePrintingDisplayRulesReference() {
        String invalid = contentJson
            .replace("\"profile_version\": \"v1\"", "\"profile_version\": \"v2\"")
            .replace("""
                "printing_display_rules": {"artifact_code": "PRINTING_DISPLAY_RULES", "artifact_version": "v1", "fingerprint_sha256": "%s"},
                """.formatted(hash("{}")), "");
        String fingerprint = validator.computeAggregateFingerprint(
            "TEST_PROFILE", "v2", StoreProfileContractValidator.SCHEMA_VERSION, invalid, artifacts
        );

        StoreProfileValidationResult result = validator.validate(
            "TEST_PROFILE",
            "v2",
            StoreProfileContractValidator.SCHEMA_VERSION,
            invalid,
            fingerprint,
            artifacts
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(StoreProfileValidationIssue::path)
            .contains("template_references.printing_display_rules");
    }

    @Test
    void newPostA11VersionOneProfilesAlsoRequirePrintingDisplayRulesReference() {
        String invalid = contentJson.replace("""
                "printing_display_rules": {"artifact_code": "PRINTING_DISPLAY_RULES", "artifact_version": "v1", "fingerprint_sha256": "%s"},
                """.formatted(hash("{}")), "");
        String fingerprint = validator.computeAggregateFingerprint(
            "TEST_PROFILE", "v1", StoreProfileContractValidator.SCHEMA_VERSION, invalid, artifacts
        );

        StoreProfileValidationResult result = validator.validate(
            "TEST_PROFILE",
            "v1",
            StoreProfileContractValidator.SCHEMA_VERSION,
            invalid,
            fingerprint,
            artifacts
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(StoreProfileValidationIssue::path)
            .contains("template_references.printing_display_rules");
    }

    @Test
    void referencesMustHaveMatchingArtifacts() {
        List<StoreProfileArtifactInput> missingDisplayRules = artifacts.stream()
            .filter(artifact -> !"PRINTING_DISPLAY_RULES".equals(artifact.artifactCode()))
            .toList();
        String fingerprint = validator.computeAggregateFingerprint(
            "TEST_PROFILE", "v1", StoreProfileContractValidator.SCHEMA_VERSION, contentJson, missingDisplayRules
        );

        StoreProfileValidationResult result = validator.validate(
            "TEST_PROFILE",
            "v1",
            StoreProfileContractValidator.SCHEMA_VERSION,
            contentJson,
            fingerprint,
            missingDisplayRules
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(StoreProfileValidationIssue::code)
            .contains("TEMPLATE_REFERENCE_ARTIFACT_MISSING");
    }

    @Test
    void rejectsUnknownHardwareCapabilityThroughA8Catalog() {
        String invalid = contentJson.replace(
            "\"PAD_DEVICE_FOR_PAD_DIRECT\"",
            "\"PAD_DEVICE_FOR_PAD_DIRECT\", \"CASH_DRAWER\""
        );
        String fingerprint = validator.computeAggregateFingerprint(
            "TEST_PROFILE", "v1", StoreProfileContractValidator.SCHEMA_VERSION, invalid, artifacts
        );

        StoreProfileValidationResult result = validator.validate(
            "TEST_PROFILE",
            "v1",
            StoreProfileContractValidator.SCHEMA_VERSION,
            invalid,
            fingerprint,
            artifacts
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(StoreProfileValidationIssue::code)
            .contains("MODULE_VALIDATION_UNKNOWN_HARDWARE_CAPABILITY");
    }

    @Test
    void rejectsProhibitedDataAndSourceStoreDbIds() {
        String invalid = contentJson.replace(
            "\"materialization_contract\": {",
            "\"password_hash\":\"nope\",\"source_store_id\":1,\"printer_endpoint\":\"10.0.0.5:9100\","
                + "\"materialization_contract\": {"
        );
        String fingerprint = validator.computeAggregateFingerprint(
            "TEST_PROFILE", "v1", StoreProfileContractValidator.SCHEMA_VERSION, invalid, artifacts
        );

        StoreProfileValidationResult result = validator.validate(
            "TEST_PROFILE",
            "v1",
            StoreProfileContractValidator.SCHEMA_VERSION,
            invalid,
            fingerprint,
            artifacts
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(StoreProfileValidationIssue::code)
            .contains("PROHIBITED_PROFILE_DATA");
    }

    @Test
    void rejectsMaterializationWithoutExplicitIdRemapping() {
        String invalid = contentJson
            .replace("\"uses_profile_local_refs\": true", "\"uses_profile_local_refs\": false")
            .replace("\"source_store_db_ids_allowed\": false", "\"source_store_db_ids_allowed\": true");
        String fingerprint = validator.computeAggregateFingerprint(
            "TEST_PROFILE", "v1", StoreProfileContractValidator.SCHEMA_VERSION, invalid, artifacts
        );

        StoreProfileValidationResult result = validator.validate(
            "TEST_PROFILE",
            "v1",
            StoreProfileContractValidator.SCHEMA_VERSION,
            invalid,
            fingerprint,
            artifacts
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(StoreProfileValidationIssue::code)
            .contains("ID_REMAPPING_REQUIRED", "SOURCE_DB_IDS_FORBIDDEN");
    }

    @Test
    void rejectsArtifactFingerprintMismatchAndDuplicateArtifactReferences() {
        List<StoreProfileArtifactInput> invalidArtifacts = List.of(
            new StoreProfileArtifactInput("MENU_TEMPLATE", "MENU_TEMPLATE", "v1", "{}", "0".repeat(64)),
            artifact("MENU_TEMPLATE", "MENU_TEMPLATE", "{}")
        );
        String fingerprint = validator.computeAggregateFingerprint(
            "TEST_PROFILE", "v1", StoreProfileContractValidator.SCHEMA_VERSION, contentJson, invalidArtifacts
        );

        StoreProfileValidationResult result = validator.validate(
            "TEST_PROFILE",
            "v1",
            StoreProfileContractValidator.SCHEMA_VERSION,
            contentJson,
            fingerprint,
            invalidArtifacts
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(StoreProfileValidationIssue::code)
            .contains("ARTIFACT_FINGERPRINT_MISMATCH", "DUPLICATE_ARTIFACT");
    }

    private StoreProfileArtifactInput artifact(String type, String code, String json) {
        return new StoreProfileArtifactInput(type, code, "v1", json, hash(json));
    }

    private String hash(String json) {
        return StoreProfileCanonicalJson.sha256Canonical(json);
    }
}
