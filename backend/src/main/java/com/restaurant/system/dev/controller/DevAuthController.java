package com.restaurant.system.dev.controller;

import com.restaurant.system.auth.dto.LoginResponse;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.dev.dto.DevSwitchUserRequest;
import com.restaurant.system.dev.dto.DevTestUserResponse;
import com.restaurant.system.dev.service.DevAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dev")
public class DevAuthController {

    private final DevAuthService devAuthService;

    public DevAuthController(DevAuthService devAuthService) {
        this.devAuthService = devAuthService;
    }

    @GetMapping("/test-users")
    public ApiResponse<List<DevTestUserResponse>> testUsers() {
        return ApiResponse.success(devAuthService.testUsers());
    }

    @PostMapping("/switch-user")
    public ApiResponse<LoginResponse> switchUser(
        @Valid @RequestBody DevSwitchUserRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(devAuthService.switchUser(request.loginIdentifier, servletRequest));
    }
}
