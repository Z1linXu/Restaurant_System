package com.restaurant.system.owner.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.modules.ModuleConfigurationInput;
import com.restaurant.system.modules.ModuleDependencyValidator;
import com.restaurant.system.modules.ModuleState;
import com.restaurant.system.modules.ModuleValidationIssue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StoreProfileContractValidator {

    public static final String SCHEMA_VERSION = "STORE_PROFILE_CONTRACT_V1";

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final Set<String> PROHIBITED_KEYS = Set.of(
        "password",
        "password_hash",
        "access_token",
        "refresh_token",
        "token",
        "cookie",
        "jwt",
        "secret",
        "credential",
        "credentials",
        "customer",
        "customer_name",
        "phone",
        "email",
        "payment",
        "payment_token",
        "printer_endpoint",
        "ip_address",
        "host",
        "ssh",
        "db_secret",
        "db_password",
        "source_store_id",
        "store_id",
        "category_id",
        "item_id",
        "option_id",
        "parent_option_id",
        "station_id",
        "table_id",
        "printer_id",
        "device_id"
    );

    private final ModuleDependencyValidator moduleDependencyValidator;

    public StoreProfileContractValidator() {
        this(ModuleDependencyValidator.loadDefault());
    }

    StoreProfileContractValidator(ModuleDependencyValidator moduleDependencyValidator) {
        this.moduleDependencyValidator = moduleDependencyValidator;
    }

    public StoreProfileValidationResult validate(
        String profileCode,
        String profileVersion,
        String schemaVersion,
        String contentJson,
        String expectedFingerprint,
        List<StoreProfileArtifactInput> artifacts
    ) {
        List<StoreProfileValidationIssue> issues = new ArrayList<>();
        if (!StoreProfileIdentity.isExact(profileCode)) {
            issues.add(issue("INVALID_PROFILE_CODE", "profile_code", "Profile code must be exact"));
        }
        if (!StoreProfileIdentity.isExact(profileVersion)) {
            issues.add(issue("INVALID_PROFILE_VERSION", "profile_version", "Profile version must be exact"));
        }
        if (!StoreProfileIdentity.isExact(schemaVersion) || !SCHEMA_VERSION.equals(schemaVersion)) {
            issues.add(issue("INVALID_SCHEMA_VERSION", "schema_version", "Unsupported Store Profile contract version"));
        }
        if (expectedFingerprint == null || !SHA256_PATTERN.matcher(expectedFingerprint).matches()) {
            issues.add(issue("INVALID_FINGERPRINT", "fingerprint_sha256", "Fingerprint must be a lowercase SHA-256 hex digest"));
        }

        JsonNode root = parseJson(contentJson, "content_json", issues);
        List<StoreProfileArtifactInput> safeArtifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        if (root != null) {
            assertTextEquals(root, "profile_code", profileCode, issues);
            assertTextEquals(root, "profile_version", profileVersion, issues);
            assertTextEquals(root, "schema_version", schemaVersion, issues);
            scanProhibitedData(root, "$", issues);
            validateModuleDefaults(root, issues);
            validateProfileReferences(root, profileCode, profileVersion, safeArtifacts, issues);
            validateMasterMenuReference(root, profileCode, profileVersion, issues);
            validateMenuTemplateShape(root, issues);
        }

        List<String> artifactFingerprints = new ArrayList<>();
        Set<String> artifactIdentities = new LinkedHashSet<>();
        for (StoreProfileArtifactInput artifact : safeArtifacts) {
            validateArtifact(artifact, artifactIdentities, artifactFingerprints, issues);
        }

        String computedFingerprint = root == null
            ? null
            : computeAggregateFingerprint(profileCode, profileVersion, schemaVersion, contentJson, safeArtifacts);
        if (computedFingerprint != null && expectedFingerprint != null && !computedFingerprint.equals(expectedFingerprint)) {
            issues.add(issue("FINGERPRINT_MISMATCH", "fingerprint_sha256", "Stored fingerprint does not match canonical content"));
        }
        return new StoreProfileValidationResult(issues.isEmpty(), computedFingerprint, issues);
    }

    public String computeAggregateFingerprint(
        String profileCode,
        String profileVersion,
        String schemaVersion,
        String contentJson,
        List<StoreProfileArtifactInput> artifacts
    ) {
        List<String> artifactLines = (artifacts == null ? List.<StoreProfileArtifactInput>of() : artifacts).stream()
            .map(artifact -> String.join("|",
                safe(artifact.artifactType()),
                safe(artifact.artifactCode()),
                safe(artifact.artifactVersion()),
                StoreProfileCanonicalJson.sha256Canonical(artifact.contentJson())
            ))
            .sorted()
            .toList();
        String canonical = String.join("\n",
            "profile_code=" + safe(profileCode),
            "profile_version=" + safe(profileVersion),
            "schema_version=" + safe(schemaVersion),
            "content=" + StoreProfileCanonicalJson.canonicalize(contentJson),
            "artifacts=" + String.join("\n", artifactLines)
        );
        return StoreProfileCanonicalJson.sha256(canonical);
    }

    private void validateArtifact(
        StoreProfileArtifactInput artifact,
        Set<String> artifactIdentities,
        List<String> artifactFingerprints,
        List<StoreProfileValidationIssue> issues
    ) {
        if (artifact == null) {
            issues.add(issue("INVALID_ARTIFACT", "artifacts", "Artifact entry is required"));
            return;
        }
        String identity = safe(artifact.artifactType()) + "|" + safe(artifact.artifactCode());
        if (!artifactIdentities.add(identity)) {
            issues.add(issue("DUPLICATE_ARTIFACT", "artifacts", "Artifact type/code must be unique per version"));
        }
        if (!StoreProfileIdentity.isExact(artifact.artifactType())) {
            issues.add(issue("INVALID_ARTIFACT_TYPE", "artifacts[].artifact_type", "Artifact type must be exact"));
        }
        if (!StoreProfileIdentity.isExact(artifact.artifactCode())) {
            issues.add(issue("INVALID_ARTIFACT_CODE", "artifacts[].artifact_code", "Artifact code must be exact"));
        }
        if (!StoreProfileIdentity.isExact(artifact.artifactVersion())) {
            issues.add(issue("INVALID_ARTIFACT_VERSION", "artifacts[].artifact_version", "Artifact version must be exact"));
        }
        JsonNode artifactRoot = parseJson(artifact.contentJson(), "artifacts[].content_json", issues);
        if (artifactRoot != null) {
            scanProhibitedData(artifactRoot, "$.artifacts." + identity, issues);
            String computed = StoreProfileCanonicalJson.sha256Canonical(artifact.contentJson());
            artifactFingerprints.add(computed);
            if (artifact.fingerprintSha256() == null || !computed.equals(artifact.fingerprintSha256())) {
                issues.add(issue("ARTIFACT_FINGERPRINT_MISMATCH", "artifacts[].fingerprint_sha256",
                    "Artifact fingerprint must match canonical artifact content"));
            }
        }
    }

    private void validateModuleDefaults(JsonNode root, List<StoreProfileValidationIssue> issues) {
        JsonNode moduleDefaults = root.path("module_defaults");
        if (!moduleDefaults.isObject()) {
            issues.add(issue("MODULE_DEFAULTS_REQUIRED", "module_defaults", "Profile must declare module defaults"));
            return;
        }
        Map<String, ModuleState> moduleStates = new LinkedHashMap<>();
        JsonNode modules = moduleDefaults.path("modules");
        if (!modules.isArray()) {
            issues.add(issue("MODULE_DEFAULTS_REQUIRED", "module_defaults.modules", "Module defaults must be an array"));
            return;
        }
        Set<String> moduleKeys = new LinkedHashSet<>();
        for (JsonNode module : modules) {
            String moduleKey = module.path("module_key").asText(null);
            if (moduleKey == null || !moduleKeys.add(moduleKey)) {
                issues.add(issue("DUPLICATE_OR_BLANK_MODULE", "module_defaults.modules", "Module keys must be present and unique"));
                continue;
            }
            moduleStates.put(moduleKey, module.path("enabled").asBoolean(false) ? ModuleState.ENABLED : ModuleState.DISABLED);
        }
        Set<String> environmentCapabilities = textSet(moduleDefaults.path("environment_capabilities"));
        Set<String> hardwareCapabilities = textSet(moduleDefaults.path("hardware_capabilities"));
        for (ModuleValidationIssue moduleIssue : moduleDependencyValidator.validate(
            new ModuleConfigurationInput(moduleStates, environmentCapabilities, hardwareCapabilities)
        ).issues()) {
            issues.add(issue("MODULE_VALIDATION_" + moduleIssue.code().name(),
                "module_defaults." + moduleIssue.moduleKey(), moduleIssue.message()));
        }
    }

    private void validateProfileReferences(
        JsonNode root,
        String profileCode,
        String profileVersion,
        List<StoreProfileArtifactInput> artifacts,
        List<StoreProfileValidationIssue> issues
    ) {
        JsonNode references = root.path("template_references");
        if (!references.isObject()) {
            issues.add(issue("TEMPLATE_REFERENCES_REQUIRED", "template_references", "Profile must reference versioned templates"));
            return;
        }
        List<String> requiredReferences = new ArrayList<>(List.of(
            "menu_template",
            "pricing_policy",
            "combo_configuration",
            "tables",
            "stations",
            "logical_printing_topology",
            "role_access_defaults",
            "hardware_requirements"
        ));
        if (requiresPrintingDisplayRules(profileCode, profileVersion)) {
            requiredReferences.add("printing_display_rules");
        }
        for (String required : requiredReferences) {
            JsonNode reference = references.path(required);
            if (!reference.isObject()
                || !StoreProfileIdentity.isExact(reference.path("artifact_code").asText(null))
                || !StoreProfileIdentity.isExact(reference.path("artifact_version").asText(null))
                || !SHA256_PATTERN.matcher(reference.path("fingerprint_sha256").asText("")).matches()) {
                issues.add(issue("TEMPLATE_REFERENCE_INVALID", "template_references." + required,
                    "Template references must include exact code, version and fingerprint"));
                continue;
            }
            if (!hasMatchingArtifact(reference, artifacts)) {
                issues.add(issue("TEMPLATE_REFERENCE_ARTIFACT_MISSING", "template_references." + required,
                    "Template reference must have a matching artifact with the same code, version and fingerprint"));
            }
        }
    }

    private boolean requiresPrintingDisplayRules(String profileCode, String profileVersion) {
        return !("ST_DENIS_CANONICAL_PROFILE".equals(profileCode) && "v1".equals(profileVersion));
    }

    private void validateMasterMenuReference(
        JsonNode root,
        String profileCode,
        String profileVersion,
        List<StoreProfileValidationIssue> issues
    ) {
        if (!requiresMasterMenuReference(profileCode, profileVersion)) {
            return;
        }
        JsonNode reference = root.path("master_menu_reference");
        if (!reference.isObject()) {
            issues.add(issue("MASTER_MENU_REFERENCE_REQUIRED", "master_menu_reference",
                "Post-A11 Store Profiles must reference a published Master Menu version"));
            return;
        }
        if (!StoreProfileIdentity.isExact(reference.path("master_menu_key").asText(null))) {
            issues.add(issue("MASTER_MENU_REFERENCE_INVALID", "master_menu_reference.master_menu_key",
                "Master Menu reference must include an exact key"));
        }
        if (!StoreProfileIdentity.isExact(reference.path("master_menu_version").asText(null))) {
            issues.add(issue("MASTER_MENU_REFERENCE_INVALID", "master_menu_reference.master_menu_version",
                "Master Menu reference must include an exact version"));
        }
        if (!StoreProfileIdentity.isExact(reference.path("schema_version").asText(null))) {
            issues.add(issue("MASTER_MENU_REFERENCE_INVALID", "master_menu_reference.schema_version",
                "Master Menu reference must include a schema version"));
        }
        if (!SHA256_PATTERN.matcher(reference.path("fingerprint_sha256").asText("")).matches()) {
            issues.add(issue("MASTER_MENU_REFERENCE_INVALID", "master_menu_reference.fingerprint_sha256",
                "Master Menu reference must include a lowercase SHA-256 fingerprint"));
        }
    }

    private boolean requiresMasterMenuReference(String profileCode, String profileVersion) {
        return !("ST_DENIS_CANONICAL_PROFILE".equals(profileCode) && "v1".equals(profileVersion));
    }

    private boolean hasMatchingArtifact(JsonNode reference, List<StoreProfileArtifactInput> artifacts) {
        String artifactCode = reference.path("artifact_code").asText(null);
        String artifactVersion = reference.path("artifact_version").asText(null);
        String fingerprint = reference.path("fingerprint_sha256").asText(null);
        for (StoreProfileArtifactInput artifact : artifacts == null ? List.<StoreProfileArtifactInput>of() : artifacts) {
            if (artifact != null
                && safe(artifact.artifactCode()).equals(safe(artifactCode))
                && safe(artifact.artifactVersion()).equals(safe(artifactVersion))
                && safe(artifact.fingerprintSha256()).equals(safe(fingerprint))) {
                return true;
            }
        }
        return false;
    }

    private void validateMenuTemplateShape(JsonNode root, List<StoreProfileValidationIssue> issues) {
        JsonNode materialization = root.path("materialization_contract");
        if (!materialization.isObject()) {
            issues.add(issue("MATERIALIZATION_CONTRACT_REQUIRED", "materialization_contract",
                "Profile must declare materialization and ID-remapping semantics"));
            return;
        }
        if (!materialization.path("uses_profile_local_refs").asBoolean(false)
            || !materialization.path("new_surrogate_ids_required").asBoolean(false)) {
            issues.add(issue("ID_REMAPPING_REQUIRED", "materialization_contract",
                "Profile materialization must use profile-local references and new target IDs"));
        }
        if (materialization.path("source_store_db_ids_allowed").asBoolean(true)) {
            issues.add(issue("SOURCE_DB_IDS_FORBIDDEN", "materialization_contract.source_store_db_ids_allowed",
                "Source Store database IDs are not reusable profile identity"));
        }
    }

    private void assertTextEquals(JsonNode root, String field, String expected, List<StoreProfileValidationIssue> issues) {
        String actual = root.path(field).asText(null);
        if (!safe(expected).equals(actual)) {
            issues.add(issue("PROFILE_IDENTITY_MISMATCH", field, "Profile content identity must match version row"));
        }
    }

    private JsonNode parseJson(String json, String path, List<StoreProfileValidationIssue> issues) {
        try {
            return StoreProfileCanonicalJson.parse(json);
        } catch (IllegalArgumentException exception) {
            issues.add(issue("INVALID_JSON", path, "JSON must parse successfully"));
            return null;
        }
    }

    private void scanProhibitedData(JsonNode node, String path, List<StoreProfileValidationIssue> issues) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                scanProhibitedData(node.get(index), path + "[" + index + "]", issues);
            }
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String normalized = key.toLowerCase();
            if (PROHIBITED_KEYS.contains(normalized) || normalized.endsWith("_token")
                || normalized.endsWith("_secret") || normalized.endsWith("_credential")) {
                issues.add(issue("PROHIBITED_PROFILE_DATA", path + "." + key,
                    "Profile content must not contain IDs, credentials, PII, physical endpoints or runtime secrets"));
            }
            scanProhibitedData(entry.getValue(), path + "." + key, issues);
        });
    }

    private Set<String> textSet(JsonNode arrayNode) {
        Set<String> values = new LinkedHashSet<>();
        if (!arrayNode.isArray()) {
            return values;
        }
        for (JsonNode value : arrayNode) {
            if (value.isTextual()) {
                values.add(value.asText());
            }
        }
        return values;
    }

    private StoreProfileValidationIssue issue(String code, String path, String message) {
        return new StoreProfileValidationIssue(code, path, message);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
