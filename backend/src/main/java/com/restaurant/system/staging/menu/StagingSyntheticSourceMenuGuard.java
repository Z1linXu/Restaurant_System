package com.restaurant.system.staging.menu;

import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.staging.bootstrap.StagingSyntheticBootstrapExecutionContext;
import com.restaurant.system.staging.bootstrap.StagingSyntheticBootstrapException;
import com.restaurant.system.staging.bootstrap.StagingSyntheticBootstrapGuard;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("staging-synthetic-bootstrap")
public class StagingSyntheticSourceMenuGuard {

    private static final String SYNTHETIC_PREFIX = "STG005_";

    private final StagingSyntheticBootstrapGuard environmentGuard;

    public StagingSyntheticSourceMenuGuard(StagingSyntheticBootstrapGuard environmentGuard) {
        this.environmentGuard = environmentGuard;
    }

    public void validate(
        StagingSyntheticBootstrapExecutionContext context,
        StagingSyntheticSourceMenuSpec spec
    ) {
        try {
            environmentGuard.validateEnvironment(context);
            environmentGuard.validateRequestBinding(
                context,
                spec == null ? null : spec.runtimeSha(),
                spec == null ? null : spec.toolSha()
            );
        } catch (StagingSyntheticBootstrapException exception) {
            throw invalid(exception.getErrorCode(), exception.getMessage());
        }
        validateSpec(spec);
    }

    public void validateSpec(StagingSyntheticSourceMenuSpec spec) {
        if (spec == null) {
            throw invalid("STG005_SOURCE_MENU_REQUEST_INVALID", "Synthetic source-menu request is required");
        }
        if (!ChinatownMenuCloneProfile.SOURCE_STORE_ID.equals(spec.sourceStoreId())) {
            throw invalid(
                "STG005_SOURCE_MENU_STORE_REJECTED",
                "Synthetic source Store must satisfy the reviewed profile source identity"
            );
        }
        if (spec.sourceStoreCode() == null
            || !spec.sourceStoreCode().equals(spec.sourceStoreCode().trim())
            || !spec.sourceStoreCode().startsWith(SYNTHETIC_PREFIX)
            || spec.sourceStoreCode().length() > 255
            || !spec.sourceStoreCode().chars().allMatch(character ->
                Character.isLetterOrDigit(character) || character == '_' || character == '-'
            )) {
            throw invalid(
                "STG005_SOURCE_MENU_STORE_REJECTED",
                "Synthetic source Store code must use the STG005_ namespace"
            );
        }
        requireFullSha(spec.runtimeSha(), "STG005_SOURCE_MENU_RUNTIME_SHA_INVALID");
        requireFullSha(spec.toolSha(), "STG005_SOURCE_MENU_TOOL_SHA_INVALID");
    }

    private void requireFullSha(String value, String errorCode) {
        if (value == null || !value.matches("[0-9a-f]{40}")) {
            throw invalid(errorCode, "A full lowercase 40-character Git SHA is required");
        }
    }

    private StagingSyntheticSourceMenuException invalid(String errorCode, String message) {
        return new StagingSyntheticSourceMenuException(errorCode, message);
    }
}
