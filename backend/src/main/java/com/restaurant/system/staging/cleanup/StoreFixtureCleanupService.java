package com.restaurant.system.staging.cleanup;

import com.restaurant.system.common.auth.AuthenticatedUser;

public interface StoreFixtureCleanupService {

    StoreFixtureCleanupResponse cleanup(
        AuthenticatedUser actor,
        Long organizationId,
        String idempotencyKey,
        StoreFixtureCleanupRequest request
    );
}
