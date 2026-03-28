package com.restaurant.system.menu.controller;

import com.restaurant.system.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menu")
public class MenuController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("menu module ready");
    }
}
