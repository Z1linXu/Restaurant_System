package com.restaurant.system.order.finish;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.modules.*;
import com.restaurant.system.order.controller.FinishAllTablesController;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class FinishAllTablesControllerTest {
    private final AuthorizationService authorization=mock(AuthorizationService.class);
    private final StoreModuleAccessEvaluator modules=mock(StoreModuleAccessEvaluator.class);
    private final FinishAllTablesService service=mock(FinishAllTablesService.class);
    private final FinishAllTablesController controller=new FinishAllTablesController(authorization,modules,service);
    @Test void usesTheSameCompleteStoreAccessAndOperationalPermissionForPreviewAndExecute() {
        var actor=new AuthenticatedUser(1L,18L,1L,"fixture","Fixture","FRONTDESK");
        when(authorization.requireForStore(18L,Capability.ORDER_COMPLETE)).thenReturn(actor);
        controller.preview(18L);controller.execute(18L,new FinishAllTablesResponse.Request("signed-plan"));
        verify(authorization,times(2)).requireForStore(18L,Capability.ORDER_COMPLETE);
        verify(modules,times(2)).requireOperationalCapability(18L,ModuleKeys.ORDERING_POS);
        verify(service).preview(18L,actor);verify(service).execute(18L,actor,"signed-plan");
    }
    @Test void noCapabilityOrForeignStoreCannotEvenPreviewOrExecute() {
        doThrow(new ForbiddenException("Store access denied")).when(authorization).requireForStore(2L,Capability.ORDER_COMPLETE);
        assertThatThrownBy(()->controller.preview(2L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(()->controller.execute(2L,new FinishAllTablesResponse.Request("plan"))).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(service,modules);
    }
    @Test void disabledOrderingCannotReachAnyBusinessOperation() {
        doThrow(new ForbiddenException("Ordering disabled")).when(modules).requireOperationalCapability(18L,ModuleKeys.ORDERING_POS);
        assertThatThrownBy(()->controller.preview(18L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(()->controller.execute(18L,new FinishAllTablesResponse.Request("plan"))).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(service);
    }
}
