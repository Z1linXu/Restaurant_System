package com.restaurant.system.owner.provisioning.part2;

import com.restaurant.system.common.auth.AuthenticatedUser;

public interface StorePart2ProvisioningService {

    StorePart2ProvisioningResponse provision(
        AuthenticatedUser actor,
        Long organizationId,
        Long storeId,
        String idempotencyKey,
        StorePart2ProvisioningRequest request
    );

    StoreReadinessResponse readiness(Long organizationId, Long storeId);

    StoreActivationResponse activate(
        AuthenticatedUser actor,
        Long organizationId,
        Long storeId,
        String idempotencyKey,
        StoreActivationRequest request
    );
}
