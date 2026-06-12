package com.restaurant.system.auth.service;

import com.restaurant.system.auth.dto.LoginRequest;
import com.restaurant.system.auth.dto.LoginResponse;
import com.restaurant.system.common.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request, HttpServletRequest servletRequest);

    LoginResponse refresh(String refreshToken, HttpServletRequest servletRequest);

    void logout(String refreshToken);

    LoginResponse me(AuthenticatedUser currentUser);
}
