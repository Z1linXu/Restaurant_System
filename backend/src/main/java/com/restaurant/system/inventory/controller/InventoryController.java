package com.restaurant.system.inventory.controller;

import com.restaurant.system.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("inventory module ready");
    }
}
