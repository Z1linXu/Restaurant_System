package com.restaurant.system.owner.provisioning;

import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.owner.dto.OwnerStoreProvisioningCatalogResponse;
import com.restaurant.system.owner.profile.StoreProfileAccessService;
import com.restaurant.system.owner.profile.StoreProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class OwnerStoreProvisioningCatalogService {
    private final StoreProfileRepository profiles;

    public OwnerStoreProvisioningCatalogService(StoreProfileRepository profiles) {
        this.profiles = profiles;
    }

    /** Caller authorizes exact Organization ownership before using this internal reader. */
    public OwnerStoreProvisioningCatalogResponse catalog(Long organizationId) {
        var profile = profiles.findByProfileCode("ST_DENIS_CANONICAL_PROFILE").orElse(null);
        try {
            StoreProfileAccessService.requireUse(profile, organizationId);
            return OwnerStoreProvisioningCatalogResponse.initial(true);
        } catch (ForbiddenException denied) {
            var response = new OwnerStoreProvisioningCatalogResponse();
            response.enabled = false;
            return response;
        }
    }
}
