package com.restaurant.system.analytics.service;

import com.restaurant.system.common.auth.*;
import com.restaurant.system.user.entity.Store;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Server-authorized financial scope. Query parameters can only narrow membership. */
@Service
public class AnalyticsReadScope {
    private final AuthorizationService authorization;
    private final StoreAccessService storeAccess;
    private final RoleCapabilityRegistry roles;

    public AnalyticsReadScope(AuthorizationService authorization, StoreAccessService storeAccess, RoleCapabilityRegistry roles) {
        this.authorization = authorization; this.storeAccess = storeAccess; this.roles = roles;
    }

    public List<Store> stores(Long organizationId, Long storeId) {
        AuthenticatedUser user = authorization.require(Capability.ADMIN_STORE_CONFIG);
        List<Store> result = storeAccess.accessibleStores(user).stream()
            .filter(store -> organizationId == null || Objects.equals(store.organization_id, organizationId))
            .filter(store -> storeId == null || Objects.equals(store.id, storeId)).toList();
        if (result.isEmpty()) throw new ForbiddenException("No authorized stores in the requested analytics scope");
        return result;
    }

    public List<Store> comparisonStores(Long organizationId, Long storeId) {
        List<Store> selected = stores(organizationId, storeId);
        return storeId == null ? selected : stores(selected.get(0).organization_id, null);
    }

    public void requireGlobalRebuild() {
        AuthenticatedUser user = authorization.require(Capability.ADMIN_STORE_CONFIG);
        if (!roles.isAdmin(user.roleCode())) throw new ForbiddenException("A store_id is required for non-admin analytics rebuild");
    }
}
