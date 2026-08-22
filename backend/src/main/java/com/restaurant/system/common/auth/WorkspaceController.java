package com.restaurant.system.common.auth;

import com.restaurant.system.common.auth.dto.StoreContextResponse;
import com.restaurant.system.common.auth.dto.WorkspaceResponse;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.modules.StoreModuleService;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.StoreOperationalState;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorkspaceController {

    private final RequestUserContextService requestUserContextService;
    private final StoreAccessService storeAccessService;
    private final StoreRepository storeRepository;
    private final OrganizationRepository organizationRepository;
    private final StoreModuleService storeModuleService;

    public WorkspaceController(
        RequestUserContextService requestUserContextService,
        StoreAccessService storeAccessService,
        StoreRepository storeRepository,
        OrganizationRepository organizationRepository,
        StoreModuleService storeModuleService
    ) {
        this.requestUserContextService = requestUserContextService;
        this.storeAccessService = storeAccessService;
        this.storeRepository = storeRepository;
        this.organizationRepository = organizationRepository;
        this.storeModuleService = storeModuleService;
    }

    @GetMapping("/me/workspaces")
    public ApiResponse<WorkspaceResponse> workspaces() {
        AuthenticatedUser user = requestUserContextService.getRequiredUser();
        WorkspaceResponse response = new WorkspaceResponse();
        List<Store> stores = storeAccessService.accessibleStores(user).stream()
            .filter(this::visibleInDefaultWorkspace)
            .toList();
        response.defaultStoreId = stores.stream()
            .filter(store -> store != null && store.id != null && store.id.equals(user.storeId()))
            .findFirst()
            .map(store -> store.id)
            .orElseGet(() -> stores.stream()
                .filter(store -> store != null && store.id != null)
                .map(store -> store.id)
                .findFirst()
                .orElse(user.storeId()));
        response.organizations = storeAccessService.accessibleOrganizations(user).stream()
            .filter(organization -> organization != null && organization.id != null)
            .map(organization -> toOrganizationWorkspace(user, organization))
            .toList();
        response.stores = stores.stream()
            .filter(store -> store != null && store.id != null)
            .map(store -> toStoreWorkspace(user, store))
            .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/stores/{storeId}/context")
    public ApiResponse<StoreContextResponse> storeContext(@PathVariable Long storeId) {
        AuthenticatedUser user = requestUserContextService.getRequiredUser();
        storeAccessService.requireStoreAccess(user, storeId);
        Store store = storeRepository.findById(storeId)
            .orElseThrow(() -> new ForbiddenException("Store not found"));
        StoreContextResponse response = new StoreContextResponse();
        response.id = store.id;
        response.name = store.name;
        response.code = store.code;
        response.status = store.status;
        response.storeKind = store.store_kind;
        response.lifecycleStatus = store.lifecycle_status;
        response.operationalState = StoreOperationalState.value(store);
        response.live = StoreOperationalState.isLive(store);
        response.provisioningSource = store.provisioning_source;
        response.provisionedProfileCode = store.provisioned_profile_code;
        response.provisionedProfileVersion = store.provisioned_profile_version;
        response.provisionedMasterMenuKey = store.provisioned_master_menu_key;
        response.provisionedMasterMenuVersion = store.provisioned_master_menu_version;
        response.organizationId = store.organization_id;
        response.roleCode = storeAccessService.roleCodeForStore(user, store);
        response.moduleConfiguration = storeModuleService.getConfiguration(storeId);
        if (store.organization_id != null) {
            organizationRepository.findById(store.organization_id).ifPresent(organization -> {
                response.organizationName = organization.name;
                response.organizationCode = organization.code;
            });
        }
        return ApiResponse.success(response);
    }

    private WorkspaceResponse.OrganizationWorkspace toOrganizationWorkspace(AuthenticatedUser user, Organization organization) {
        WorkspaceResponse.OrganizationWorkspace response = new WorkspaceResponse.OrganizationWorkspace();
        response.id = organization.id;
        response.name = organization.name;
        response.code = organization.code;
        response.status = organization.status;
        response.roleCode = storeAccessService.roleCodeForOrganization(user, organization.id);
        if (response.roleCode == null) {
            response.roleCode = user.roleCode();
        }
        return response;
    }

    private WorkspaceResponse.StoreWorkspace toStoreWorkspace(AuthenticatedUser user, Store store) {
        WorkspaceResponse.StoreWorkspace response = new WorkspaceResponse.StoreWorkspace();
        response.id = store.id;
        response.name = store.name;
        response.code = store.code;
        response.status = store.status;
        response.storeKind = store.store_kind;
        response.lifecycleStatus = store.lifecycle_status;
        response.operationalState = StoreOperationalState.value(store);
        response.live = StoreOperationalState.isLive(store);
        response.provisioningSource = store.provisioning_source;
        response.organizationId = store.organization_id;
        response.roleCode = storeAccessService.roleCodeForStore(user, store);
        return response;
    }

    private boolean visibleInDefaultWorkspace(Store store) {
        if (store == null) {
            return false;
        }
        return !"VALIDATION_FIXTURE".equalsIgnoreCase(store.store_kind)
            || "PHASE_B_OWNER_PROVISIONING".equalsIgnoreCase(store.provisioning_source);
    }
}
