package com.restaurant.system.owner.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoreMenuCloneOptionPlanValidatorTest {

    private final StoreMenuCloneOptionPlanValidator validator = new StoreMenuCloneOptionPlanValidator();

    @Test
    void returnsOnlyDeterministicSafeDiagnosticsForMalformedPlans() {
        List<StoreMenuClonePlannedOption> plan = List.of(
            option(10L, " CHILD ", "missing_parent", 1),
            option(10L, "child", null, 2),
            option(99L, "outside", null, 0)
        );

        StoreMenuCloneOptionPlanValidator.ValidationResult result = validator.validate(plan, Set.of(10L));

        assertThat(result.valid()).isFalse();
        assertThat(result.missingCodes()).containsExactly("missing_parent");
        assertThat(result.duplicateCodes()).containsExactly("child");
        assertThat(result.warnings()).containsExactly(
            "OPTION_PLAN_FIELD_INVALID",
            "OPTION_PLAN_TARGET_SCOPE_INVALID"
        );
    }

    @Test
    void reportsNullAndNonCanonicalFieldsInsteadOfThrowingBeforeValidation() {
        StoreMenuClonePlannedOption invalid = new StoreMenuClonePlannedOption(
            null, null, null, null, " ADD_ON ", null, null, null, null, null, null
        );

        StoreMenuCloneOptionPlanValidator.ValidationResult result = validator.validate(
            List.of(invalid),
            Set.of(10L)
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.missingCodes()).isEmpty();
        assertThat(result.duplicateCodes()).isEmpty();
        assertThat(result.warnings()).containsExactly(
            "OPTION_PLAN_FIELD_INVALID",
            "OPTION_PLAN_TARGET_SCOPE_INVALID"
        );
    }

    private StoreMenuClonePlannedOption option(Long targetItemId, String code, String parentCode, int sortOrder) {
        return new StoreMenuClonePlannedOption(
            targetItemId,
            null,
            "addon",
            code,
            "ADD_ON",
            parentCode,
            sortOrder,
            "safe-name",
            "safe-name",
            BigDecimal.ZERO,
            true
        );
    }
}
