package com.restaurant.system.owner.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The single full validator for an in-memory option plan. Validate and execute must invoke this
 * component before execution persists any option rows.
 */
@Component
public final class StoreMenuCloneOptionPlanValidator {

    public static final String RESULT_CODE = "TARGET_MENU_VALIDATION_FAILED";
    private static final int MAX_DIAGNOSTIC_CODES = 100;

    public ValidationResult validate(List<StoreMenuClonePlannedOption> options, Set<Long> allowedTargetItemIds) {
        List<String> missingCodes = new ArrayList<>();
        List<String> duplicateCodes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (options == null || allowedTargetItemIds == null || allowedTargetItemIds.isEmpty()) {
            return invalid(missingCodes, duplicateCodes, List.of("OPTION_PLAN_TARGET_SCOPE_INVALID"));
        }

        Map<OptionKey, StoreMenuClonePlannedOption> byKey = new HashMap<>();
        for (StoreMenuClonePlannedOption option : options) {
            if (option == null) {
                warnings.add("OPTION_PLAN_ENTRY_INVALID");
                continue;
            }
            if (option.targetItemId() == null || !allowedTargetItemIds.contains(option.targetItemId())) {
                warnings.add("OPTION_PLAN_TARGET_SCOPE_INVALID");
            }
            if (!exact(option.optionType()) || !exact(option.optionCode()) || !exact(option.optionGroup())
                || option.sortOrder() == null || option.sortOrder() <= 0 || option.priceDelta() == null
                || option.active() == null) {
                warnings.add("OPTION_PLAN_FIELD_INVALID");
            }
            String code = normalized(option.optionCode());
            if (code == null) {
                continue;
            }
            OptionKey key = new OptionKey(option.targetItemId(), code);
            if (byKey.putIfAbsent(key, option) != null) {
                duplicateCodes.add(code);
            }
        }

        for (Map.Entry<OptionKey, StoreMenuClonePlannedOption> entry : byKey.entrySet()) {
            StoreMenuClonePlannedOption option = entry.getValue();
            if (option.parentOptionCode() == null) {
                continue;
            }
            String parentCode = normalized(option.parentOptionCode());
            if (!exact(option.parentOptionCode()) || parentCode == null) {
                warnings.add("OPTION_PLAN_PARENT_INVALID");
                continue;
            }
            OptionKey parentKey = new OptionKey(option.targetItemId(), parentCode);
            if (entry.getKey().equals(parentKey)) {
                warnings.add("OPTION_PLAN_PARENT_SELF_REFERENCE");
            } else if (!byKey.containsKey(parentKey)) {
                missingCodes.add(parentCode);
            }
        }
        if (hasParentCycle(byKey)) {
            warnings.add("OPTION_PLAN_PARENT_CYCLE");
        }
        return invalid(missingCodes, duplicateCodes, warnings);
    }

    private ValidationResult invalid(List<String> missingCodes, List<String> duplicateCodes, List<String> warnings) {
        List<String> normalizedMissing = stable(missingCodes);
        List<String> normalizedDuplicates = stable(duplicateCodes);
        List<String> normalizedWarnings = stable(warnings);
        boolean valid = normalizedMissing.isEmpty() && normalizedDuplicates.isEmpty() && normalizedWarnings.isEmpty();
        return new ValidationResult(valid, normalizedMissing, normalizedDuplicates, normalizedWarnings);
    }

    private boolean hasParentCycle(Map<OptionKey, StoreMenuClonePlannedOption> byKey) {
        Set<OptionKey> visiting = new HashSet<>();
        Set<OptionKey> visited = new HashSet<>();
        for (OptionKey key : byKey.keySet()) {
            if (visit(key, byKey, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean visit(
        OptionKey key,
        Map<OptionKey, StoreMenuClonePlannedOption> byKey,
        Set<OptionKey> visiting,
        Set<OptionKey> visited
    ) {
        if (visited.contains(key)) {
            return false;
        }
        if (!visiting.add(key)) {
            return true;
        }
        StoreMenuClonePlannedOption option = byKey.get(key);
        String parentCode = option == null ? null : normalized(option.parentOptionCode());
        boolean cyclic = parentCode != null && visit(new OptionKey(key.targetItemId(), parentCode), byKey, visiting, visited);
        visiting.remove(key);
        visited.add(key);
        return cyclic;
    }

    private List<String> stable(List<String> values) {
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .sorted(Comparator.naturalOrder())
            .limit(MAX_DIAGNOSTIC_CODES)
            .toList();
    }

    private boolean exact(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private record OptionKey(Long targetItemId, String optionCode) {
    }

    public record ValidationResult(
        boolean valid,
        List<String> missingCodes,
        List<String> duplicateCodes,
        List<String> warnings
    ) {
    }
}
