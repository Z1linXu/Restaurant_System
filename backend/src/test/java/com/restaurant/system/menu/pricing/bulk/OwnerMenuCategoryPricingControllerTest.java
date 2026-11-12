package com.restaurant.system.menu.pricing.bulk;

import static com.restaurant.system.menu.pricing.bulk.CategoryBulkPriceModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OwnerMenuCategoryPricingControllerTest {
    private CategoryBulkPriceService service;
    private AuthorizationService authorization;
    private StoreModuleAccessEvaluator modules;
    private OwnerMenuCategoryPricingController controller;
    private MockMvc mvc;
    private final AuthenticatedUser actor = new AuthenticatedUser(8L, 18L, 1L, "owner", "Owner", "OWNER");

    @BeforeEach void setUp() {
        service = mock(CategoryBulkPriceService.class); authorization = mock(AuthorizationService.class);
        modules = mock(StoreModuleAccessEvaluator.class);
        controller = new OwnerMenuCategoryPricingController(service, authorization, modules);
        when(authorization.requireManagerOrOwnerForStore(18L, Capability.ADMIN_MENU_MANAGE)).thenReturn(actor);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void capabilitiesRequireExistingMenuAuthorizationAndModule() throws Exception {
        when(service.capabilities(18L)).thenReturn(new PricingCapabilities(18L, true, false));
        mvc.perform(get("/api/v1/admin/menu/pricing-capabilities?store_id=18"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.category_bulk_enabled").value(true))
            .andExpect(jsonPath("$.data.standard_size_enabled").value(false));
        verify(authorization).requireManagerOrOwnerForStore(18L, Capability.ADMIN_MENU_MANAGE);
        verify(modules).requireCapability(18L, ModuleKeys.MENU_MANAGEMENT);
    }

    @Test void previewAndApplyCannotReachServiceWhenStoreAuthorizationFails() {
        when(authorization.requireManagerOrOwnerForStore(18L, Capability.ADMIN_MENU_MANAGE)).thenThrow(new ForbiddenException("Access denied"));
        var preview = new PreviewRequest(); preview.store_id = 18L;
        var apply = new ApplyRequest(); apply.store_id = 18L;
        assertThatThrownBy(() -> controller.preview(preview)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.apply(apply)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(service, modules);
    }

    @Test void moduleDisabledBlocksBothPreviewAndApply() {
        doThrow(new ForbiddenException("Module disabled")).when(modules).requireCapability(18L, ModuleKeys.MENU_MANAGEMENT);
        var preview = new PreviewRequest(); preview.store_id = 18L;
        var apply = new ApplyRequest(); apply.store_id = 18L;
        assertThatThrownBy(() -> controller.preview(preview)).hasMessage("Module disabled");
        assertThatThrownBy(() -> controller.apply(apply)).hasMessage("Module disabled");
        verifyNoInteractions(service);
    }

    @Test void missingStoreCannotAuthorizeAnUnscopedMutation() throws Exception {
        mvc.perform(post("/api/v1/admin/menu/category-pricing/apply").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("CATEGORY_PRICING_STORE_REQUIRED"));
        verifyNoInteractions(authorization, modules, service);
    }

    @Test void expiredApplyReturnsConflictWithMachineReadableError() throws Exception {
        when(service.apply(any(), eq(actor))).thenThrow(new CategoryPricingException(HttpStatus.CONFLICT,
            "CATEGORY_PRICING_PREVIEW_EXPIRED", "Preview has expired."));
        mvc.perform(post("/api/v1/admin/menu/category-pricing/apply").contentType(MediaType.APPLICATION_JSON).content("{\"store_id\":18}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error_code").value("CATEGORY_PRICING_PREVIEW_EXPIRED"));
        verify(modules).requireCapability(18L, ModuleKeys.MENU_MANAGEMENT);
    }

    @Test void malformedDecimalIsBadRequestAndCannotReachTheMutation() throws Exception {
        mvc.perform(post("/api/v1/admin/menu/category-pricing/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"store_id\":18,\"amount\":\"not-money\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("CATEGORY_PRICING_PAYLOAD_INVALID"));
        verifyNoInteractions(service);
    }

    @Test void applyUsesAuthorizedActorAndDoesNotEmitASecondBestEffortAudit() {
        var request = new ApplyRequest(); request.store_id = 18L; request.category_ids = List.of(2L);
        controller.apply(request);
        verify(service).apply(request, actor);
        verify(authorization).requireManagerOrOwnerForStore(18L, Capability.ADMIN_MENU_MANAGE);
        verify(modules).requireCapability(18L, ModuleKeys.MENU_MANAGEMENT);
    }
}
