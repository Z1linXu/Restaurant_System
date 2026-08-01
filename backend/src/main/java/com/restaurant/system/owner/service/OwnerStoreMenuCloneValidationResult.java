package com.restaurant.system.owner.service;

import java.util.List;

public record OwnerStoreMenuCloneValidationResult(
    boolean valid,
    String profileCode,
    Long sourceMenuRevision,
    Long targetMenuRevision,
    int expectedStationCount,
    int expectedCategoryCount,
    int expectedItemCount,
    int expectedOptionCount,
    List<String> missingCodes,
    List<String> duplicateCodes,
    List<String> warnings
) {

    public OwnerStoreMenuCloneValidationResult {
        missingCodes = List.copyOf(missingCodes);
        duplicateCodes = List.copyOf(duplicateCodes);
        warnings = List.copyOf(warnings);
    }
}
