package com.restaurant.system.delivery;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import java.util.Map;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}")
public class StorePresentationController {
    private final StorePresentationService service; private final AuthorizationService auth; private final StoreModuleAccessEvaluator modules;
    public StorePresentationController(StorePresentationService service,AuthorizationService auth,StoreModuleAccessEvaluator modules){this.service=service;this.auth=auth;this.modules=modules;}
    @GetMapping("/presentation") public ApiResponse<Map<String,Object>> get(@PathVariable Long storeId){manage(storeId);return ApiResponse.success(service.publicView(storeId));}
    @PutMapping("/presentation") public ApiResponse<Map<String,Object>> save(@PathVariable Long storeId,@RequestBody Map<String,Object> request){manage(storeId);return ApiResponse.success(service.save(storeId,request));}
    @GetMapping("/receipt-presentation") public ApiResponse<StorePresentationService.ReceiptSettings> receipts(@PathVariable Long storeId){print(storeId);return ApiResponse.success(service.receipts(storeId));}
    @PutMapping("/receipt-presentation") public ApiResponse<StorePresentationService.ReceiptSettings> saveReceipts(@PathVariable Long storeId,@RequestBody StorePresentationService.ReceiptSettings request){print(storeId);return ApiResponse.success(service.saveReceipts(storeId,request));}
    private void manage(Long id){auth.requireManagerOrOwnerForStore(id,Capability.ADMIN_MENU_MANAGE);}
    private void print(Long id){auth.requireManagerOrOwnerForStore(id,Capability.ADMIN_PRINTING_MANAGE);modules.requireOperationalModuleEnabled(id,ModuleKeys.PRINTING);}
    @ExceptionHandler(StorePresentationService.ReceiptSettingsConflict.class)
    public ResponseEntity<ApiResponse<Void>> staleReceipt(StorePresentationService.ReceiptSettingsConflict error){return ResponseEntity.status(409).body(ApiResponse.failure(error.getMessage()));}
}
