package com.restaurant.system.auth.service;

import com.restaurant.system.common.auth.AuthenticatedUser;

public interface TokenService {

    String createAccessToken(AuthenticatedUser user, Long organizationId);

    AccessTokenClaims parseAccessToken(String token);

    String generateRefreshToken();

    String hashRefreshToken(String refreshToken);

    long getAccessTokenExpirationSeconds();

    record AccessTokenClaims(
        Long userId,
        Long roleId,
        Long storeId,
        Long organizationId,
        String roleCode,
        long issuedAt,
        long expiresAt
    ) {
    }
}
