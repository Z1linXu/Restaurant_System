package com.restaurant.system.owner.profile;

public record StoreProfileValidationIssue(
    String code,
    String path,
    String message
) {
}
