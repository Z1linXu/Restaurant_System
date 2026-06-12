package com.restaurant.system.auth.controller;

import com.restaurant.system.auth.dto.LoginRequest;
import com.restaurant.system.auth.dto.LoginResponse;
import com.restaurant.system.auth.dto.RefreshTokenRequest;
import com.restaurant.system.auth.service.AuthService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.RequestUserContextService;
import com.restaurant.system.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RequestUserContextService requestUserContextService;

    public AuthController(
        AuthService authService,
        RequestUserContextService requestUserContextService
    ) {
        this.authService = authService;
        this.requestUserContextService = requestUserContextService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.login(request, servletRequest));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
        @Valid @RequestBody RefreshTokenRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(authService.refresh(request.refreshToken, servletRequest));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken);
        return ApiResponse.success("Logged out", null);
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> me() {
        AuthenticatedUser currentUser = requestUserContextService.getRequiredUser();
        return ApiResponse.success(authService.me(currentUser));
    }
}
