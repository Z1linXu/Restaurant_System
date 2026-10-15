package com.restaurant.system.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.merchant.dto.MerchantConfigurationResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/merchant-configuration")
public class MerchantConfigurationController {
    private final MerchantConfigurationService service;
    public MerchantConfigurationController(MerchantConfigurationService service) { this.service = service; }
    @GetMapping public ApiResponse<MerchantConfigurationResponse> get(@PathVariable Long storeId) {
        return ApiResponse.success(service.get(storeId));
    }
    @PutMapping public ApiResponse<MerchantConfigurationResponse> update(@PathVariable Long storeId,
        @RequestBody JsonNode body, HttpServletRequest request) { return ApiResponse.success(service.update(storeId, body, request)); }
}
