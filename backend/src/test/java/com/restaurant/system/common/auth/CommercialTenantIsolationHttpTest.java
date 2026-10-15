package com.restaurant.system.common.auth;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.auth.filter.AuthTokenFilter;
import com.restaurant.system.audit.controller.AuditLogController;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.menu.entity.*;
import com.restaurant.system.menu.repository.*;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.modules.StoreModuleService;
import com.restaurant.system.order.repository.*;
import com.restaurant.system.platform.controller.*;
import com.restaurant.system.platform.dto.PlatformAdminOverviewResponse;
import com.restaurant.system.platform.repository.*;
import com.restaurant.system.platform.service.PlatformAdminService;
import com.restaurant.system.station.entity.*;
import com.restaurant.system.station.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.RequestContextFilter;

/** Actual Bearer filter and authorization services; only persistence/business work are synthetic. */
class CommercialTenantIsolationHttpTest {
    CommercialSecurityFixture f;
    MockMvc mvc;
    PlatformAdminService service;
    AuditLogService audit;
    AuthorizationService auth;
    StationRepository stations;
    DiningTableRepository tables;
    MenuCategoryRepository categories;
    MenuItemRepository items;
    MenuItemOptionRepository options;
    StoreKdsDisplayConfigRepository kds;
    StoreModuleAccessEvaluator modules;
    @BeforeEach void setup() {
        f=new CommercialSecurityFixture();
        auth=new AuthorizationService(f.context,f.capabilities,f.access,mock(OrderRepository.class),mock(OrderItemRepository.class),mock(KitchenTaskRepository.class));
        stations=mock(StationRepository.class);tables=mock(DiningTableRepository.class);categories=mock(MenuCategoryRepository.class);
        items=mock(MenuItemRepository.class);options=mock(MenuItemOptionRepository.class);kds=mock(StoreKdsDisplayConfigRepository.class);
        var templates=mock(RestaurantTemplateRepository.class);when(templates.findAllByOrderByIdAsc()).thenReturn(List.of());
        for (long store : new long[]{10,20}) {
            Station station=new Station();station.id=store;station.store_id=store;when(stations.findById(store)).thenReturn(Optional.of(station));
            DiningTable table=new DiningTable();table.id=store;table.store_id=store;when(tables.findById(store)).thenReturn(Optional.of(table));
            MenuCategory category=new MenuCategory();category.id=store;category.store_id=store;when(categories.findById(store)).thenReturn(Optional.of(category));
            MenuItem item=new MenuItem();item.id=store;item.store_id=store;when(items.findById(store)).thenReturn(Optional.of(item));
            MenuItemOption option=new MenuItemOption();option.id=store;option.menu_item_id=store;when(options.findById(store)).thenReturn(Optional.of(option));
            var config=new com.restaurant.system.platform.entity.StoreKdsDisplayConfig();config.id=store;config.store_id=store;when(kds.findById(store)).thenReturn(Optional.of(config));
        }
        var guard=new PlatformResourceAuthorization(auth,f.context,f.access,new OwnerOrganizationAuthorizationService(f.orgMembers,f.organizations,f.stores),f.stores,templates,stations,tables,categories,items,options,kds,f.users,f.roles);
        service=mock(PlatformAdminService.class);audit=mock(AuditLogService.class);modules=mock(StoreModuleAccessEvaluator.class);
        when(service.saveStation(any())).thenAnswer(i -> i.getArgument(0));
        when(service.saveMenuItem(any())).thenAnswer(i -> i.getArgument(0));
        when(service.saveMenuItemOption(any())).thenAnswer(i -> i.getArgument(0));
        when(service.getRoles()).thenAnswer(i -> new ArrayList<>(f.roleRows.values()));
        var platform=new PlatformAdminController(service,auth,mock(FeatureFlagService.class),audit,modules,guard);
        var workspace=new WorkspaceController(f.context,f.access,f.stores,f.organizations,mock(StoreModuleService.class));
        var staffService=new com.restaurant.system.staff.service.impl.StaffAdminServiceImpl(f.users,f.roles,f.stores,
            mock(com.restaurant.system.auth.repository.UserCredentialRepository.class),mock(com.restaurant.system.auth.service.PasswordService.class),
            auth,audit,modules,f.access,f.storeMembers);
        var staff=new com.restaurant.system.staff.controller.StaffAdminController(staffService,auth,f.context);
        var aggregation=mock(com.restaurant.system.analytics.service.AnalyticsAggregationService.class);
        var readScope=new com.restaurant.system.analytics.service.AnalyticsReadScope(auth,f.access,f.capabilities);
        var analytics=new com.restaurant.system.analytics.controller.AnalyticsAdminController(aggregation,auth,mock(FeatureFlagService.class),modules,readScope);
        mvc=MockMvcBuilders.standaloneSetup(platform,workspace,new AuditLogController(audit,auth,modules),staff,analytics)
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestContextFilter(),new AuthTokenFilter(f.identities,new ObjectMapper())).build();
    }
    @Test void anonymousAndForgedDevelopmentHeaderCannotAccessWorkspace() throws Exception {
        mvc.perform(get("/api/v1/me/workspaces")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/workspaces").header("X-User-Id","1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/workspaces").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void workspaceContainsOnlyOwnersOrganizationAndRevokedDefaultIsNotAdvertised() throws Exception {
        mvc.perform(get("/api/v1/me/workspaces").header("Authorization",f.bearer(1)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.stores.length()").value(2))
            .andExpect(jsonPath("$.data.organizations[0].id").value(100));
        mvc.perform(get("/api/v1/me/workspaces").header("Authorization",f.bearer(9)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.stores").isEmpty()).andExpect(jsonPath("$.data.default_store_id").doesNotExist());
        mvc.perform(get("/api/v1/stores/20/context").header("Authorization",f.bearer(1))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/stores/10/context").header("Authorization",f.bearer(5))).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"stations","dining-tables","menu/categories","menu/items","kds-configs"})
    void ownBodyCannotSmuggleForeignPathOrPostId(String resource) throws Exception {
        String body="{\"id\":20,\"store_id\":10}";
        mvc.perform(put("/api/v1/admin/platform/"+resource+"/20").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/platform/"+resource).header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void foreignMenuCategoryStationAndParentOptionReferencesAreRejected() throws Exception {
        for (String body : List.of("{\"store_id\":10,\"category_id\":20}","{\"store_id\":10,\"station_id\":20}"))
            mvc.perform(post("/api/v1/admin/platform/menu/items").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/platform/menu/item-options").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON).content("{\"menu_item_id\":10,\"parent_option_id\":20}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/platform/menu/item-options/20").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON).content("{\"menu_item_id\":10}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void legitimateOwnStoreMenuAndStationWritesStillWork() throws Exception {
        mvc.perform(put("/api/v1/admin/platform/stations/10").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON).content("{\"store_id\":10,\"code\":\"KITCHEN\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/platform/menu/items").header("Authorization",f.bearer(4)).contentType(MediaType.APPLICATION_JSON).content("{\"store_id\":10,\"category_id\":10,\"station_id\":10}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/platform/menu/item-options").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON).content("{\"menu_item_id\":10}")).andExpect(status().isOk());
    }
    @Test void mixedGlobalOwnerCannotManageStoreAsMembershipFrontdesk() throws Exception {
        mvc.perform(post("/api/v1/admin/platform/stations").header("Authorization",f.bearer(7)).contentType(MediaType.APPLICATION_JSON).content("{\"store_id\":11}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void merchantCannotMintAdminOrChangeSharedRolePresets() throws Exception {
        mvc.perform(post("/api/v1/admin/platform/users").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON).content("{\"store_id\":10,\"role_id\":4}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/platform/roles/3").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"OWNER\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void foreignUserCannotBeMovedBySpoofingOwnStoreBody() throws Exception {
        mvc.perform(put("/api/v1/admin/platform/users/2").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON).content("{\"store_id\":10,\"role_id\":3}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void auditRequiresExplicitAuthorizedStoreAndNeverRunsGlobalRead() throws Exception {
        mvc.perform(get("/api/v1/admin/audit-logs").header("Authorization",f.bearer(1))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit-logs").param("store_id","20").header("Authorization",f.bearer(1))).andExpect(status().isForbidden());
        verifyNoInteractions(audit);
        mvc.perform(get("/api/v1/admin/audit-logs").param("store_id","10").header("Authorization",f.bearer(1))).andExpect(status().isOk());
        verify(audit).search(eq(10L),isNull(),isNull(),isNull(),eq(0),eq(50));
    }
    @Test void metadataListsAreScopedAndAdminHasNoBusinessAccess() throws Exception {
        mvc.perform(get("/api/v1/admin/platform/stores").header("Authorization",f.bearer(1)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/v1/admin/platform/organizations").header("Authorization",f.bearer(1)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(100));
        mvc.perform(get("/api/v1/admin/platform/stores").header("Authorization",f.bearer(5))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }    @Test void staffListsOnlyManagedStoresAndRejectsForeignTargetMove() throws Exception {
        mvc.perform(get("/api/v1/admin/staff/stores").header("Authorization",f.bearer(1)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/v1/admin/staff/stores").header("Authorization",f.bearer(7)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(put("/api/v1/admin/staff/4").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"store_id\":20,\"username\":\"synthetic_user_4\",\"role_code\":\"FRONTDESK\"}"))
            .andExpect(status().isForbidden());
        verify(f.users,never()).save(any());
    }
    @Test void inactiveIdentityAndRevokedMembershipAreRejectedOnEveryHttpRequest() throws Exception {
        String bearer=f.bearer(4);
        f.userRows.get(4L).setStatus("inactive");
        mvc.perform(get("/api/v1/stores/10/context").header("Authorization",bearer)).andExpect(status().isUnauthorized());
        f.userRows.get(4L).setStatus("active");f.member(4,10,"FRONTDESK",false);
        mvc.perform(get("/api/v1/stores/10/context").header("Authorization",bearer)).andExpect(status().isForbidden());
    }
    @Test void ownerCannotResetOrDeactivatePlatformOperatorAccount() throws Exception {
        mvc.perform(post("/api/v1/admin/staff/5/deactivate").header("Authorization",f.bearer(1)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/staff/5/reset-password").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"new_password\":\"SyntheticOnly!Password9\"}"))
            .andExpect(status().isForbidden());
        verify(f.users,never()).save(any());
    }
    @Test void aggregateReportsCannotUseGlobalOwnerToBypassStoreFrontdeskRole() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/summaries").param("organization_id","100").header("Authorization",f.bearer(7)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/analytics/rebuild").param("date","2026-10-15").header("Authorization",f.bearer(5)))
            .andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"foreign-org", "foreign-store", "empty-org"})
    void dormantGrantsCannotBeRevivedByRoleChangeOrAccountControl(String grant) throws Exception {
        if (grant.equals("foreign-store")) f.member(4,20,"OWNER",true);
        else if (grant.equals("foreign-org")) f.orgMember(4,200,"OWNER",true);
        else {
            var organization = new com.restaurant.system.platform.entity.Organization();
            organization.id=300L; organization.status="active"; f.organizationRows.put(300L,organization);
            f.orgMember(4,300,"OWNER",true);
        }
        long ownerRole=f.roleRows.values().stream().filter(r -> "OWNER".equals(r.getCode())).findFirst().orElseThrow().getId();
        mvc.perform(put("/api/v1/admin/platform/users/4").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"store_id\":10,\"role_id\":"+ownerRole+"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/staff/4").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"store_id\":10,\"username\":\"synthetic_user_4\",\"role_code\":\"OWNER\"}"))
            .andExpect(status().isForbidden());
        for (String operation : List.of("reactivate","deactivate"))
            mvc.perform(post("/api/v1/admin/staff/4/"+operation).header("Authorization",f.bearer(1))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/staff/4/reset-password").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"new_password\":\"SyntheticOnly!Password9\"}"))
            .andExpect(status().isForbidden());
        verify(f.users,never()).save(any()); verifyNoInteractions(service);
        org.assertj.core.api.Assertions.assertThat(f.actor(4).roleCode()).isEqualTo("FRONTDESK");
        if (grant.equals("foreign-store")) org.assertj.core.api.Assertions.assertThat(f.access.forStore(f.actor(4),20L).roleCode()).isEqualTo("FRONTDESK");
        else org.assertj.core.api.Assertions.assertThat(f.access.canAccessStore(f.actor(4),20L)).isFalse();
    }

    @Test void managerCanStillManageOwnFrontdeskWithOrdinaryOrganizationMembership() throws Exception {
        f.orgMember(4,100,"FRONTDESK",true);
        when(f.roles.findFirstByCodeIgnoreCase(anyString())).thenAnswer(i -> f.roleRows.values().stream().filter(role -> role.getCode().equalsIgnoreCase(i.getArgument(0))).findFirst());
        when(f.users.save(any())).thenAnswer(i -> i.getArgument(0));
        mvc.perform(put("/api/v1/admin/staff/4").header("Authorization",f.bearer(3)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"store_id\":10,\"username\":\"synthetic_user_4\",\"role_code\":\"FRONTDESK\"}"))
            .andExpect(status().isOk());
    }

    @Test void legitimateOwnerStaffRoleAndSameOrganizationMoveSynchronizeMembership() throws Exception {
        when(f.roles.findFirstByCodeIgnoreCase(anyString())).thenAnswer(i -> f.roleRows.values().stream().filter(role -> role.getCode().equalsIgnoreCase(i.getArgument(0))).findFirst());
        when(f.users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(f.storeMembers.save(any())).thenAnswer(i -> {
            var member=(com.restaurant.system.user.entity.StoreMembership)i.getArgument(0);
            f.membershipRows.put(member.userId+":"+member.storeId,member);return member;
        });
        mvc.perform(put("/api/v1/admin/staff/4").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"store_id\":10,\"username\":\"synthetic_user_4\",\"role_code\":\"MANAGER\"}"))
            .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(f.access.forStore(f.actor(4),10L).roleCode()).isEqualTo("MANAGER");
        mvc.perform(put("/api/v1/admin/staff/4").header("Authorization",f.bearer(1)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"store_id\":11,\"username\":\"synthetic_user_4\",\"role_code\":\"FRONTDESK\"}"))
            .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(f.access.canAccessStore(f.actor(4),11L)).isTrue();
        org.assertj.core.api.Assertions.assertThat(f.access.canAccessStore(f.actor(4),10L)).isFalse();
        org.assertj.core.api.Assertions.assertThat(f.membershipRows.get("4:10").isActive).isFalse();
    }

}
