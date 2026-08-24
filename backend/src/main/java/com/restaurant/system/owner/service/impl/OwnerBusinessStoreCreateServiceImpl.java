package com.restaurant.system.owner.service.impl;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.owner.dto.OwnerBusinessStoreCreateResponse;
import com.restaurant.system.owner.dto.OwnerStoreProvisioningRequest;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningCommand;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningResult;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningService;
import com.restaurant.system.owner.provisioning.StoreProvisioningPurpose;
import com.restaurant.system.owner.service.OwnerBusinessStoreCreateService;
import com.restaurant.system.user.StoreOperationalState;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OwnerBusinessStoreCreateServiceImpl implements OwnerBusinessStoreCreateService {

    private final OwnerStoreProvisioningService provisioningService;
    private final StoreRepository storeRepository;

    public OwnerBusinessStoreCreateServiceImpl(
        OwnerStoreProvisioningService provisioningService,
        StoreRepository storeRepository
    ) {
        this.provisioningService = provisioningService;
        this.storeRepository = storeRepository;
    }

    @Override
    public OwnerBusinessStoreCreateResponse create(
        AuthenticatedUser owner,
        Long organizationId,
        String idempotencyKey,
        OwnerStoreProvisioningRequest request
    ) {
        OwnerStoreProvisioningResult result = provisioningService.provision(new OwnerStoreProvisioningCommand(
            owner,
            organizationId,
            idempotencyKey,
            request.store_name,
            request.store_code,
            request.profile_code,
            request.profile_version,
            request.profile_fingerprint_sha256,
            request.master_menu_key,
            request.master_menu_version,
            request.master_menu_fingerprint_sha256,
            StoreProvisioningPurpose.BUSINESS
        ));
        Store store = storeRepository.findById(result.storeId())
            .orElseThrow(() -> conflict("BUSINESS_STORE_CREATE_RESULT_UNAVAILABLE", "Created Store is unavailable"));
        if (!organizationId.equals(store.organization_id)
            || !"BUSINESS".equalsIgnoreCase(store.store_kind)
            || !StoreOperationalState.isLive(store)) {
            throw conflict("BUSINESS_STORE_CREATE_RESULT_INVALID", "Created Store is not a LIVE Business Store");
        }
        return OwnerBusinessStoreCreateResponse.from(result, store);
    }

    private OwnerStoreProvisioningException conflict(String code, String message) {
        return new OwnerStoreProvisioningException(code, HttpStatus.CONFLICT, message);
    }
}
