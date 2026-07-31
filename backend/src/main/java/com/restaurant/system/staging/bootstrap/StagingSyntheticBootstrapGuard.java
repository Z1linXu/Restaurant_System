package com.restaurant.system.staging.bootstrap;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class StagingSyntheticBootstrapGuard {

    public static final String EXPECTED_PROJECT = "restaurant-pos-staging";
    public static final String EXPECTED_ROOT = "/srv/restaurant-pos/staging";
    public static final String EXPECTED_DATABASE = "restaurant_pos_staging";
    public static final String EXPECTED_DATABASE_USER = "restaurant_pos_staging";
    public static final String BOOTSTRAP_PROFILE = "staging-synthetic-bootstrap";
    public static final String CLOUD_PROFILE = "cloud";
    private static final String SYNTHETIC_PREFIX = "STG005_";
    private static final Set<String> ALLOWED_PROFILES = Set.of(CLOUD_PROFILE, BOOTSTRAP_PROFILE);

    public void validate(
        StagingSyntheticBootstrapExecutionContext context,
        StagingSyntheticBootstrapSpec spec
    ) {
        if (context == null) {
            throw invalid("STG005_BOOTSTRAP_CONTEXT_INVALID", "Bootstrap execution context is required");
        }
        validateProfiles(context.activeProfiles());
        requireExact(context.composeProject(), EXPECTED_PROJECT, "STG005_BOOTSTRAP_PROJECT_REJECTED");
        requireExact(context.stagingRoot(), EXPECTED_ROOT, "STG005_BOOTSTRAP_ROOT_REJECTED");
        requireFullSha(context.expectedRuntimeSha(), "STG005_BOOTSTRAP_RUNTIME_SHA_INVALID");
        requireFullSha(context.observedRuntimeSha(), "STG005_BOOTSTRAP_RUNTIME_SHA_INVALID");
        requireFullSha(context.toolSha(), "STG005_BOOTSTRAP_TOOL_SHA_INVALID");
        if (!context.expectedRuntimeSha().equals(context.observedRuntimeSha())) {
            throw invalid("STG005_BOOTSTRAP_RUNTIME_SHA_MISMATCH", "Observed runtime SHA does not match the approved runtime SHA");
        }
        if (!context.toolSha().equals(spec == null ? null : spec.toolSha())) {
            throw invalid("STG005_BOOTSTRAP_TOOL_SHA_MISMATCH", "Bootstrap tool SHA does not match the request");
        }
        if (!context.expectedRuntimeSha().equals(spec == null ? null : spec.runtimeSha())) {
            throw invalid("STG005_BOOTSTRAP_RUNTIME_SHA_MISMATCH", "Bootstrap request is not bound to the approved runtime SHA");
        }
        if (!"DISABLED".equals(normalizeUpper(context.printingMode())) || context.printingFeatureEnabled()) {
            throw invalid("STG005_BOOTSTRAP_PRINTING_REJECTED", "Bootstrap requires printing mode DISABLED and the printing feature disabled");
        }
        requireExact(normalizeLower(context.webApplicationType()), "none", "STG005_BOOTSTRAP_WEB_MODE_REJECTED");
        requireExact(databaseName(context.datasourceUrl()), EXPECTED_DATABASE, "STG005_BOOTSTRAP_DATABASE_REJECTED");
        requireExact(context.datasourceUsername(), EXPECTED_DATABASE_USER, "STG005_BOOTSTRAP_DATABASE_REJECTED");
        validateSpec(spec);
    }

    public void validateSpec(StagingSyntheticBootstrapSpec spec) {
        if (spec == null) {
            throw invalid("STG005_BOOTSTRAP_REQUEST_INVALID", "Bootstrap request is required");
        }
        requireSynthetic(spec.runId(), 120, "run ID");
        requireSynthetic(spec.organizationName(), 255, "organization name");
        requireSynthetic(spec.organizationCode(), 255, "organization code");
        requireSynthetic(spec.sourceStoreName(), 255, "source Store name");
        requireSynthetic(spec.sourceStoreCode(), 255, "source Store code");
        requireSynthetic(spec.ownerLoginIdentifier(), 255, "owner login");
        requireSynthetic(spec.ownerFullName(), 255, "owner display name");
        requireFullSha(spec.runtimeSha(), "STG005_BOOTSTRAP_RUNTIME_SHA_INVALID");
        requireFullSha(spec.toolSha(), "STG005_BOOTSTRAP_TOOL_SHA_INVALID");
    }

    private void validateProfiles(Set<String> activeProfiles) {
        Set<String> normalized = activeProfiles == null
            ? Set.of()
            : activeProfiles.stream()
                .filter(profile -> profile != null && !profile.isBlank())
                .map(this::normalizeLower)
                .collect(Collectors.toUnmodifiableSet());
        if (!normalized.equals(ALLOWED_PROFILES)) {
            throw invalid(
                "STG005_BOOTSTRAP_PROFILE_REJECTED",
                "Bootstrap requires exactly the cloud and staging-synthetic-bootstrap profiles"
            );
        }
    }

    private void requireSynthetic(String value, int maxLength, String field) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() > maxLength || !normalized.startsWith(SYNTHETIC_PREFIX)) {
            throw invalid("STG005_BOOTSTRAP_NAME_REJECTED", field + " must use the STG005_ prefix");
        }
        boolean safeCharacters = normalized.chars().allMatch(character ->
            Character.isLetterOrDigit(character)
                || character == '_'
                || character == '-'
                || character == ' '
        );
        if (!safeCharacters) {
            throw invalid("STG005_BOOTSTRAP_NAME_REJECTED", field + " contains unsupported characters");
        }
    }

    private void requireFullSha(String value, String errorCode) {
        if (value == null || !value.matches("[0-9a-f]{40}")) {
            throw invalid(errorCode, "A full lowercase 40-character Git SHA is required");
        }
    }

    private void requireExact(String actual, String expected, String errorCode) {
        if (!expected.equals(actual)) {
            throw invalid(errorCode, "Bootstrap environment identity does not match the approved Staging environment");
        }
    }

    private String databaseName(String datasourceUrl) {
        if (datasourceUrl == null) {
            return null;
        }
        String withoutQuery = datasourceUrl.split("\\?", 2)[0];
        int slash = withoutQuery.lastIndexOf('/');
        return slash < 0 ? null : withoutQuery.substring(slash + 1);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeLower(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeUpper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private StagingSyntheticBootstrapException invalid(String errorCode, String message) {
        return new StagingSyntheticBootstrapException(errorCode, message);
    }
}
