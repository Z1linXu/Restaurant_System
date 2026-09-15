package com.restaurant.system.menu.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.addon.StoreAddonService;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OwnerMenuAddonControllerTest {
    private StoreAddonService service;
    private AuthorizationService authorization;
    private StoreModuleAccessEvaluator modules;
    private OwnerMenuAddonController controller;

    @BeforeEach void setup() {
        service = mock(StoreAddonService.class);
        authorization = mock(AuthorizationService.class);
        modules = mock(StoreModuleAccessEvaluator.class);
        controller = new OwnerMenuAddonController(service, authorization, modules, mock(AuditLogService.class));
    }

    @Test void listUsesExistingMenuCapabilityAndEnvelopeWithoutReconciliation() {
        var response = new StoreAddonService.AddonList(List.of(), List.of());
        when(service.getAddons(12L)).thenReturn(response);
        assertThat(controller.list(12L).getData()).isSameAs(response);
        verify(authorization).requireForStore(12L, Capability.ADMIN_MENU_MANAGE);
        verify(modules).requireCapability(12L, ModuleKeys.MENU_MANAGEMENT);
        verify(service, never()).reconcile(anyLong(), any(Boolean.class));
    }

    @Test void updateAuthorizesStoredStoreRatherThanPayloadStore() {
        when(service.storeIdForAddon(80L)).thenReturn(12L);
        when(authorization.requireForStore(12L, Capability.ADMIN_MENU_MANAGE)).thenThrow(new BusinessException("FORBIDDEN"));
        var request = new StoreAddonService.WriteRequest(); request.store_id = 99L;
        assertThatThrownBy(() -> controller.update(80L, request, null)).hasMessage("FORBIDDEN");
        verify(service, never()).update(anyLong(), any());
        verify(authorization, never()).requireForStore(99L, Capability.ADMIN_MENU_MANAGE);
    }

    @Test void eligibilityCannotReachMutationWithoutItemStoreAuthority() {
        when(service.storeIdForItem(10L)).thenReturn(12L);
        when(authorization.requireForStore(12L, Capability.ADMIN_MENU_MANAGE)).thenThrow(new BusinessException("FORBIDDEN"));
        var request = new StoreAddonService.EligibilityRequest(); request.enabled = true;
        assertThatThrownBy(() -> controller.eligibility(10L, 80L, request, null)).hasMessage("FORBIDDEN");
        verify(service, never()).setEligibility(anyLong(), anyLong(), any(), anyLong());
    }

    @Test void missingDryRunFlagCannotAccidentallyMutate() {
        var request = new StoreAddonService.ReconcileRequest(); request.store_id = 12L;
        assertThatThrownBy(() -> controller.reconcile(request, null)).hasMessage("ADDON_RECONCILE_DRY_RUN_REQUIRED");
        verify(service, never()).reconcile(anyLong(), any(Boolean.class));
    }

    @Test void reconciliationAuthorizesExactStoreAndReturnsSanitizedReport() {
        var request = new StoreAddonService.ReconcileRequest(); request.store_id = 12L; request.dry_run = true;
        var report = new StoreAddonService.ReconciliationReport(12L, true, 0, 0, List.of());
        when(service.reconcile(12L, true)).thenReturn(report);
        assertThat(controller.reconcile(request, null).getData()).isSameAs(report);
        verify(authorization).requireForStore(12L, Capability.ADMIN_MENU_MANAGE);
        verify(modules).requireCapability(12L, ModuleKeys.MENU_MANAGEMENT);
    }
}
