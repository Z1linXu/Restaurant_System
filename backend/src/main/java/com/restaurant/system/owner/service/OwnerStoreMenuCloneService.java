package com.restaurant.system.owner.service;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneRequest;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneResponse;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneValidationResponse;

public interface OwnerStoreMenuCloneService {

    OwnerStoreMenuCloneValidationResponse validateMenuClone(
        Long organizationId,
        Long targetStoreId,
        OwnerStoreMenuCloneRequest request,
        AuthenticatedUser actor
    );

    OwnerStoreMenuCloneResponse cloneMenu(
        Long organizationId,
        Long targetStoreId,
        String idempotencyKey,
        OwnerStoreMenuCloneRequest request,
        AuthenticatedUser actor
    );
}
