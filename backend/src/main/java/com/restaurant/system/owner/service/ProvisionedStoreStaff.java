package com.restaurant.system.owner.service;

/**
 * Safe result for the onboarding orchestration. It intentionally has no
 * credential hash or plaintext password field.
 */
public record ProvisionedStoreStaff(Long userId, Long storeId, String loginIdentifier, String roleCode) {
}
