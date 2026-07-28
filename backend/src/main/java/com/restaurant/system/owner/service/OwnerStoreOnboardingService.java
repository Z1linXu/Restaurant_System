package com.restaurant.system.owner.service;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingRequest;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingResponse;

public interface OwnerStoreOnboardingService {

    OwnerStoreOnboardingResponse onboard(
        Long organizationId,
        String idempotencyKey,
        OwnerStoreOnboardingRequest request,
        AuthenticatedUser actor
    );
}
