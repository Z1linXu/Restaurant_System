package com.restaurant.system.analytics.controller;

import com.restaurant.system.analytics.dto.AnalyticsSummaryResponse;
import com.restaurant.system.analytics.service.AnalyticsAggregationService;
import com.restaurant.system.analytics.service.AnalyticsReadScope;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.platform.controller.OwnerDashboardController;
import com.restaurant.system.platform.dto.OwnerDashboardResponse;
import com.restaurant.system.platform.service.OwnerDashboardService;
import com.restaurant.system.user.entity.Store;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsScopeAuthorizationTest {
    AuthorizationService auth = mock(AuthorizationService.class);
    StoreAccessService access = mock(StoreAccessService.class);
    AnalyticsAggregationService analytics = mock(AnalyticsAggregationService.class);
    OwnerDashboardService dashboard = mock(OwnerDashboardService.class);
    AnalyticsReadScope scope = new AnalyticsReadScope(auth, access, new RoleCapabilityRegistry());
    AuthenticatedUser owner = new AuthenticatedUser(10L, 1L, 1L, "owner", "Owner", "OWNER");
    Store own = store(1L, 1L), foreign = store(2L, 2L);
    MockMvc mvc;

    @BeforeEach void setup() {
        when(auth.require(Capability.ADMIN_STORE_CONFIG)).thenReturn(owner);
        when(access.accessibleStores(owner)).thenReturn(List.of(own));
        var modules = mock(StoreModuleAccessEvaluator.class);
        mvc = MockMvcBuilders.standaloneSetup(
            new AnalyticsAdminController(analytics, auth, mock(FeatureFlagService.class), modules, scope),
            new OwnerDashboardController(dashboard, auth, modules, scope)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void capableOwnerCannotReadAnotherOrganizationFinancialsOnEitherEndpoint() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/summaries").param("organization_id", "2")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard").param("organization_id", "2")).andExpect(status().isForbidden());
        verifyNoInteractions(analytics, dashboard);
    }

    @Test void accessibleStoreCannotBeUsedToSmuggleForeignOrganization() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/summaries").param("store_id", "1").param("organization_id", "2")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard").param("store_id", "1").param("organization_id", "2")).andExpect(status().isForbidden());
        verifyNoInteractions(analytics, dashboard);
    }

    @Test void ownersOwnOrganizationReportsRemainAvailable() throws Exception {
        when(analytics.getSummaries(eq(1L), isNull(), eq("today"), isNull(), isNull(), isNull())).thenReturn(new AnalyticsSummaryResponse());
        when(dashboard.getDashboard(1L, null, "today", false)).thenReturn(new OwnerDashboardResponse());
        mvc.perform(get("/api/v1/admin/analytics/summaries").param("organization_id", "1")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/dashboard").param("organization_id", "1")).andExpect(status().isOk());
        assertThat(scope.stores(null, null)).extracting(store -> store.id).containsExactly(1L);
        assertThat(scope.comparisonStores(1L, 1L)).extracting(store -> store.id).containsExactly(1L);
    }

    @Test void ownerCannotTriggerLegacyGlobalRebuild() throws Exception {
        mvc.perform(post("/api/v1/admin/analytics/rebuild").param("date", "2026-10-03")).andExpect(status().isForbidden());
        verifyNoInteractions(analytics);
    }

    @Test void globalAdminRetainsExplicitOrganizationAndGlobalAccess() throws Exception {
        AuthenticatedUser admin = new AuthenticatedUser(99L, null, 99L, "admin", "Admin", "ADMIN");
        when(auth.require(Capability.ADMIN_STORE_CONFIG)).thenReturn(admin);
        when(access.accessibleStores(admin)).thenReturn(List.of(own, foreign));
        when(analytics.getSummaries(eq(2L), isNull(), eq("today"), isNull(), isNull(), isNull())).thenReturn(new AnalyticsSummaryResponse());
        mvc.perform(get("/api/v1/admin/analytics/summaries").param("organization_id", "2")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/analytics/rebuild").param("date", "2026-10-03")).andExpect(status().isOk());
        assertThat(scope.stores(null, null)).extracting(store -> store.id).containsExactly(1L, 2L);
    }

    @Test void storeManagerComparisonNeverExpandsToUnassignedSameOrganizationStores() {
        AuthenticatedUser manager = new AuthenticatedUser(20L, 1L, 2L, "manager", "Manager", "MANAGER");
        when(auth.require(Capability.ADMIN_STORE_CONFIG)).thenReturn(manager);
        when(access.accessibleStores(manager)).thenReturn(List.of(own));
        assertThat(scope.comparisonStores(1L, 1L)).extracting(store -> store.id).containsExactly(1L);
    }

    static Store store(Long id, Long organization) { Store store = new Store(); store.id = id; store.organization_id = organization; return store; }
}
