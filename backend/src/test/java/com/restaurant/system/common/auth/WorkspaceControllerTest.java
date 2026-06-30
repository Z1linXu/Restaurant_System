package com.restaurant.system.common.auth;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class WorkspaceControllerTest {

    @Mock
    private RequestUserContextService requestUserContextService;
    @Mock
    private StoreAccessService storeAccessService;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private OrganizationRepository organizationRepository;

    private MockMvc mockMvc;
    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        WorkspaceController controller = new WorkspaceController(
            requestUserContextService,
            storeAccessService,
            storeRepository,
            organizationRepository
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        user = new AuthenticatedUser(1L, 10L, 1L, "owner", "Owner User", "OWNER");
    }

    @Test
    void workspacesReturnsAccessibleStores() throws Exception {
        Organization organization = organization(100L);
        Store store = store(10L, 100L);
        when(requestUserContextService.getRequiredUser()).thenReturn(user);
        when(storeAccessService.accessibleOrganizations(user)).thenReturn(List.of(organization));
        when(storeAccessService.accessibleStores(user)).thenReturn(List.of(store));
        when(storeAccessService.roleCodeForOrganization(user, 100L)).thenReturn("OWNER");
        when(storeAccessService.roleCodeForStore(user, store)).thenReturn("OWNER");

        mockMvc.perform(get("/api/v1/me/workspaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.default_store_id").value(10))
            .andExpect(jsonPath("$.data.organizations[0].id").value(100))
            .andExpect(jsonPath("$.data.stores[0].id").value(10))
            .andExpect(jsonPath("$.data.stores[0].role_code").value("OWNER"));
    }

    @Test
    void workspacesReturnsEmptyResponseWhenNoStoreAccessExists() throws Exception {
        when(requestUserContextService.getRequiredUser()).thenReturn(user);
        when(storeAccessService.accessibleOrganizations(user)).thenReturn(List.of());
        when(storeAccessService.accessibleStores(user)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/me/workspaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.default_store_id").value(10))
            .andExpect(jsonPath("$.data.organizations").isArray())
            .andExpect(jsonPath("$.data.stores").isArray());
    }

    @Test
    void workspacesUsesFirstAccessibleStoreWhenUserDefaultStoreIsUnavailable() throws Exception {
        Store store = store(20L, 100L);
        when(requestUserContextService.getRequiredUser()).thenReturn(user);
        when(storeAccessService.accessibleOrganizations(user)).thenReturn(List.of());
        when(storeAccessService.accessibleStores(user)).thenReturn(List.of(store));
        when(storeAccessService.roleCodeForStore(user, store)).thenReturn("OWNER");

        mockMvc.perform(get("/api/v1/me/workspaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.default_store_id").value(20))
            .andExpect(jsonPath("$.data.stores[0].id").value(20));
    }

    @Test
    void storeContextReturnsContextWhenAllowed() throws Exception {
        Store store = store(10L, 100L);
        Organization organization = organization(100L);
        when(requestUserContextService.getRequiredUser()).thenReturn(user);
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        when(organizationRepository.findById(100L)).thenReturn(Optional.of(organization));
        when(storeAccessService.roleCodeForStore(user, store)).thenReturn("OWNER");

        mockMvc.perform(get("/api/v1/stores/10/context"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(10))
            .andExpect(jsonPath("$.data.organization_id").value(100))
            .andExpect(jsonPath("$.data.organization_name").value("Org 100"))
            .andExpect(jsonPath("$.data.role_code").value("OWNER"));
    }

    @Test
    void storeContextRejectsUnauthorizedStore() throws Exception {
        when(requestUserContextService.getRequiredUser()).thenReturn(user);
        doThrow(new ForbiddenException("Access denied for store 99"))
            .when(storeAccessService).requireStoreAccess(user, 99L);

        mockMvc.perform(get("/api/v1/stores/99/context"))
            .andExpect(status().isForbidden());
    }

    private Organization organization(Long id) {
        Organization organization = new Organization();
        organization.id = id;
        organization.name = "Org " + id;
        organization.code = "ORG_" + id;
        organization.status = "active";
        return organization;
    }

    private Store store(Long id, Long organizationId) {
        Store store = new Store();
        store.id = id;
        store.organization_id = organizationId;
        store.name = "Store " + id;
        store.code = "STORE_" + id;
        store.status = "active";
        return store;
    }
}
