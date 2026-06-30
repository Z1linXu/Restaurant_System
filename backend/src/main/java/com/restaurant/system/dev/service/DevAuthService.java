package com.restaurant.system.dev.service;

import com.restaurant.system.auth.dto.LoginResponse;
import com.restaurant.system.dev.dto.DevTestUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public interface DevAuthService {

    List<DevTestUserResponse> testUsers();

    LoginResponse switchUser(String loginIdentifier, HttpServletRequest request);
}
