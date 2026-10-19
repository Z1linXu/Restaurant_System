package com.restaurant.system.owner.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoreConfigurationPackageContractTest {
    private final StoreProfileContractValidator validator = new StoreProfileContractValidator();
    private ObjectNode content;
    private List<StoreProfileArtifactInput> artifacts;

    @BeforeEach void prepareVersionedPrivateConfigurationArchive() {
        content = (ObjectNode) StoreProfileCanonicalJson.parse(NeutralRestaurantProfile.CONTENT);
        content.put("profile_code", "TEST_CONFIGURATION_PACKAGE");
        content.put("configuration_kind", "STORE_CONFIGURATION_PACKAGE");
        content.putObject("base_profile_reference").put("profile_code", "NEUTRAL_RESTAURANT").put("profile_version", "v1");
        var defaults = (ObjectNode) content.path("module_defaults");
        var environments = defaults.putArray("environment_capabilities");
        List.of("CORE_POS_RUNTIME", "AUTH_RUNTIME", "DATABASE", "WEBSOCKET_RUNTIME", "ADMIN_RUNTIME",
            "PRINTING_FEATURE_FLAG", "PRINT_MODE_RUNTIME", "ANALYTICS_FEATURE_FLAG").forEach(environments::add);
        var hardware = defaults.putArray("hardware_capabilities");
        List.of("TOUCH_CLIENT", "PRINTER_TOPOLOGY_FOR_REAL_OR_PAD_DIRECT", "PAD_DEVICE_FOR_PAD_DIRECT").forEach(hardware::add);
        content.putObject("materialization_contract").put("uses_profile_local_refs", true)
            .put("new_surrogate_ids_required", true).put("source_store_db_ids_allowed", false);
        var references = content.putObject("template_references");
        artifacts = new ArrayList<>();
        for (var reference : Map.of("menu_template", "MENU_TEMPLATE", "pricing_policy", "PRICING_POLICY",
            "combo_configuration", "COMBO_CONFIGURATION", "tables", "TABLE_TEMPLATE", "stations", "STATION_TEMPLATE",
            "logical_printing_topology", "PRINTING_TOPOLOGY", "role_access_defaults", "ROLE_ACCESS_DEFAULTS",
            "hardware_requirements", "HARDWARE_REQUIREMENTS", "printing_display_rules", "PRINTING_DISPLAY_RULES").entrySet()) {
            String fingerprint = StoreProfileCanonicalJson.sha256Canonical("{}");
            references.putObject(reference.getKey()).put("artifact_code", reference.getValue())
                .put("artifact_version", "v1").put("fingerprint_sha256", fingerprint);
            artifacts.add(new StoreProfileArtifactInput(reference.getValue(), reference.getValue(), "v1", "{}", fingerprint));
        }
    }

    @Test void validNeutralBasedPackageUsesTheExistingArtifactAndAggregateFingerprintContract() {
        assertThat(validate().valid()).isTrue();
        assertThat(content.has("master_menu_reference")).isFalse();
        String first = aggregate(); content.put("brand_name", "Changed restaurant");
        assertThat(aggregate()).isNotEqualTo(first);
    }

    @Test void packageRejectsWrongOrMissingBaseAndUnknownConfigurationKind() {
        ((ObjectNode) content.path("base_profile_reference")).put("profile_version", "v2");
        assertThat(validate().issues()).extracting(StoreProfileValidationIssue::code).contains("BASE_PROFILE_REFERENCE_INVALID");
        ((ObjectNode) content.path("base_profile_reference")).put("profile_version", "v1").put("profile_code", "ST_DENIS_CANONICAL_PROFILE");
        assertThat(validate().issues()).extracting(StoreProfileValidationIssue::code).contains("BASE_PROFILE_REFERENCE_INVALID");
        content.remove("base_profile_reference");
        assertThat(validate().issues()).extracting(StoreProfileValidationIssue::code).contains("BASE_PROFILE_REFERENCE_INVALID");
        content.put("configuration_kind", "SILENT_IMPORT");
        assertThat(validate().issues()).extracting(StoreProfileValidationIssue::code).contains("CONFIGURATION_KIND_INVALID");
    }

    @Test void packageCannotCarryAMasterReferenceThatTheLegacyMaterializerCouldAccept() {
        content.putObject("master_menu_reference").put("master_menu_key", "SOME_MASTER");
        assertThat(validate().issues()).extracting(StoreProfileValidationIssue::code)
            .contains("CONFIGURATION_PACKAGE_MASTER_REFERENCE_FORBIDDEN");
    }

    @Test void packageStillRequiresAllNineArtifactsAndRejectsIdsSecretsAndInvalidModuleDefaults() {
        artifacts.removeIf(artifact -> "MENU_TEMPLATE".equals(artifact.artifactCode()));
        content.putObject("runtime_data").put("store_id", 123).put("password", "prohibited-test-value");
        ((ObjectNode) content.path("module_defaults").path("modules").get(1)).put("enabled", false);
        assertThat(validate().issues()).extracting(StoreProfileValidationIssue::code)
            .contains("TEMPLATE_REFERENCE_ARTIFACT_MISSING", "PROHIBITED_PROFILE_DATA", "MODULE_VALIDATION_CORE_MODULE_DISABLED");
    }

    @Test void originalLanzhouV1ExemptionAndLaterMasterRequirementRemainUnchanged() {
        content.remove(List.of("configuration_kind", "base_profile_reference"));
        content.put("profile_code", "ST_DENIS_CANONICAL_PROFILE");
        assertThat(validate().valid()).isTrue();
        content.put("profile_version", "v2");
        assertThat(validate().issues()).extracting(StoreProfileValidationIssue::code).contains("MASTER_MENU_REFERENCE_REQUIRED");
    }

    @Test void reviewedNeutralContentAndFingerprintStayExact() {
        assertThat(validator.validate(NeutralRestaurantProfile.CODE, NeutralRestaurantProfile.VERSION,
            NeutralRestaurantProfile.SCHEMA, NeutralRestaurantProfile.CONTENT, NeutralRestaurantProfile.FINGERPRINT, List.of()).valid()).isTrue();
    }

    @Test void completePythonArchiveUsesTheExactJavaCanonicalFingerprintAndPassesTheRealContract() throws Exception {
        Path root = repositoryRoot();
        Process process = new ProcessBuilder("python3", root.resolve("scripts/local-demo/allo_profile.py").toString(), "build")
            .directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        var payload = StoreProfileCanonicalJson.parse(output);
        List<StoreProfileArtifactInput> inputs = new ArrayList<>();
        for (var artifact : payload.path("artifacts")) {
            String encoded = artifact.path("content_json").asText();
            assertThat(encoded).isEqualTo(StoreProfileCanonicalJson.canonicalize(encoded));
            assertThat(artifact.path("fingerprint_sha256").asText()).isEqualTo(StoreProfileCanonicalJson.sha256Canonical(encoded));
            inputs.add(new StoreProfileArtifactInput(artifact.path("artifact_type").asText(), artifact.path("artifact_code").asText(),
                artifact.path("artifact_version").asText(), encoded, artifact.path("fingerprint_sha256").asText()));
        }
        assertThat(inputs).hasSize(9);
        assertThat(payload.path("visibility").asText()).isEqualTo("PRIVATE");
        String encoded = payload.path("content_json").asText();
        assertThat(encoded).isEqualTo(StoreProfileCanonicalJson.canonicalize(encoded));
        String fingerprint = validator.computeAggregateFingerprint(payload.path("profile_code").asText(),
            payload.path("profile_version").asText(), payload.path("schema_version").asText(), encoded, inputs);
        assertThat(payload.path("fingerprint_sha256").asText()).isEqualTo(fingerprint);
        var result = validator.validate(payload.path("profile_code").asText(), payload.path("profile_version").asText(),
            payload.path("schema_version").asText(), encoded, fingerprint, inputs);
        assertThat(result.issues()).isEmpty();
        assertThat(result.valid()).isTrue();
        assertThat(StoreProfileCanonicalJson.parse(encoded).has("master_menu_reference")).isFalse();
    }

    private Path repositoryRoot() {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("scripts/local-demo/allo_profile.py"))) candidate = candidate.getParent();
        if (candidate == null) throw new IllegalStateException("Cannot locate the checked-in explicit local Profile tool");
        return candidate;
    }

    private String aggregate() {
        return validator.computeAggregateFingerprint(content.path("profile_code").asText(), content.path("profile_version").asText(),
            StoreProfileContractValidator.SCHEMA_VERSION, content.toString(), artifacts);
    }
    private StoreProfileValidationResult validate() {
        return validator.validate(content.path("profile_code").asText(), content.path("profile_version").asText(),
            StoreProfileContractValidator.SCHEMA_VERSION, content.toString(), aggregate(), artifacts);
    }
}
