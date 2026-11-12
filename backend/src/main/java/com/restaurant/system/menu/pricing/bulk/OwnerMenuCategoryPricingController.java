package com.restaurant.system.menu.pricing.bulk;

import static com.restaurant.system.menu.pricing.bulk.CategoryBulkPriceModels.*;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/menu")
public class OwnerMenuCategoryPricingController {
    private final CategoryBulkPriceService service;
    private final AuthorizationService authorization;
    private final StoreModuleAccessEvaluator modules;

    public OwnerMenuCategoryPricingController(CategoryBulkPriceService service, AuthorizationService authorization,
        StoreModuleAccessEvaluator modules) {
        this.service = service;
        this.authorization = authorization;
        this.modules = modules;
    }

    @GetMapping("/pricing-capabilities")
    public ApiResponse<PricingCapabilities> capabilities(@RequestParam("store_id") Long storeId) {
        requireStore(storeId);
        return ApiResponse.success(service.capabilities(storeId));
    }

    @PostMapping("/category-pricing/preview")
    public ApiResponse<Preview> preview(@RequestBody PreviewRequest request) {
        requireStore(request == null ? null : request.store_id);
        return ApiResponse.success(service.preview(request));
    }

    @PostMapping("/category-pricing/apply")
    public ApiResponse<ApplyResult> apply(@RequestBody ApplyRequest request) {
        AuthenticatedUser actor = requireStore(request == null ? null : request.store_id);
        return ApiResponse.success(service.apply(request, actor));
    }

    @ExceptionHandler(CategoryPricingException.class)
    public ResponseEntity<ApiResponse<Void>> handlePricingFailure(CategoryPricingException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPayload(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("CATEGORY_PRICING_PAYLOAD_INVALID", "Enter a valid category price adjustment."));
    }

    private AuthenticatedUser requireStore(Long storeId) {
        if (storeId == null || storeId <= 0) throw new CategoryPricingException(org.springframework.http.HttpStatus.BAD_REQUEST,
            "CATEGORY_PRICING_STORE_REQUIRED", "Store is required.");
        AuthenticatedUser user = authorization.requireManagerOrOwnerForStore(storeId, Capability.ADMIN_MENU_MANAGE);
        modules.requireCapability(storeId, ModuleKeys.MENU_MANAGEMENT);
        return user;
    }
}
