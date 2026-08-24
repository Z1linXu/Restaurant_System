package com.restaurant.system.owner.service;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.owner.dto.OwnerBusinessStoreCreateResponse;
import com.restaurant.system.owner.dto.OwnerStoreProvisioningRequest;

public interface OwnerBusinessStoreCreateService {

    OwnerBusinessStoreCreateResponse create(
        AuthenticatedUser owner,
        Long organizationId,
        String idempotencyKey,
        OwnerStoreProvisioningRequest request
    );
}
