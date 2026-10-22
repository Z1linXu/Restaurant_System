package com.restaurant.system.menu.remove;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import java.util.List;
import org.junit.jupiter.api.Test;

class OwnerMenuRemoveControllerTest {
    private final StoreRemoveService service=mock(StoreRemoveService.class);
    private final AuthorizationService authorization=mock(AuthorizationService.class);
    private final StoreModuleAccessEvaluator modules=mock(StoreModuleAccessEvaluator.class);
    private final OwnerMenuRemoveController controller=new OwnerMenuRemoveController(service,authorization,modules,mock(AuditLogService.class));

    @Test void listUsesExistingScopedMenuCapabilityAndNeverReconciles() {
        var catalog=new StoreRemoveService.Catalog(List.of(),List.of(),List.of());when(service.getRemoves(2L)).thenReturn(catalog);
        assertThat(controller.list(2L).getData()).isSameAs(catalog);
        verify(authorization).requireForStore(2L,Capability.ADMIN_MENU_MANAGE);verify(modules).requireCapability(2L,ModuleKeys.MENU_MANAGEMENT);
        verify(service,never()).reconcile(anyLong(),anyBoolean());
    }
    @Test void storedScopeIsAuthorizedBeforeCallerScopeCanReachMutation() {
        when(service.storeIdForRemove(80L)).thenReturn(2L);
        when(authorization.requireForStore(2L,Capability.ADMIN_MENU_MANAGE)).thenThrow(new BusinessException("FORBIDDEN"));
        var body=new StoreRemoveService.WriteRequest();body.store_id=99L;
        assertThatThrownBy(()->controller.update(80L,99L,body,null)).hasMessage("FORBIDDEN");
        verify(authorization,never()).requireForStore(99L,Capability.ADMIN_MENU_MANAGE);verify(service,never()).update(anyLong(),any());
    }
    @Test void baselineOperationKeepsExistingStoreCapabilityAndCannotWriteWhenUnauthorized() {
        var baseline = mock(StoreRemoveBaselineReconciler.class);
        controller.setBaselineReconciler(baseline);
        var body = new StoreRemoveBaselineReconciler.Request();body.store_id = 2L;body.dry_run = false;
        body.expected_before_fingerprint = "confirmed";
        when(authorization.requireForStore(2L,Capability.ADMIN_MENU_MANAGE)).thenThrow(new BusinessException("FORBIDDEN"));
        assertThatThrownBy(() -> controller.baselineReconcile(body)).hasMessage("FORBIDDEN");
        verifyNoInteractions(baseline);
    }

    @Test void suppliedCrossStoreAndMissingDryRunFailClosed() {
        when(service.storeIdForRemove(80L)).thenReturn(2L);
        assertThatThrownBy(()->controller.applicability(80L,99L)).hasMessage("REMOVE_STORE_MISMATCH");
        var body=new StoreRemoveService.ReconcileRequest();body.store_id=2L;
        assertThatThrownBy(()->controller.reconcile(body,null)).hasMessage("REMOVE_RECONCILE_DRY_RUN_REQUIRED");
        verify(service,never()).setApplicability(anyLong(),any());verify(service,never()).reconcile(anyLong(),anyBoolean());
    }
}
