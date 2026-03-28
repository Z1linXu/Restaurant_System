package com.restaurant.system.user.controller;

import com.restaurant.system.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("user module ready");
    }
}
