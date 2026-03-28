package com.restaurant.system.common.auth;

public record AuthenticatedUser(
    Long userId,
    Long storeId,
    Long roleId,
    String username,
    String fullName,
    String roleCode
) {
}
