package com.restaurant.system.dev.service.impl;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.auth.dto.AuthUserResponse;
import com.restaurant.system.auth.dto.LoginResponse;
import com.restaurant.system.auth.entity.RefreshToken;
import com.restaurant.system.auth.repository.RefreshTokenRepository;
import com.restaurant.system.auth.service.TokenService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.RoleCapabilityRegistry;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.dev.DevRoleSwitcherAccess;
import com.restaurant.system.dev.DevTestUser;
import com.restaurant.system.dev.dto.DevTestUserResponse;
import com.restaurant.system.dev.service.DevAuthService;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DevAuthServiceImpl implements DevAuthService {

    private final DevRoleSwitcherAccess devRoleSwitcherAccess;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StoreRepository storeRepository;
    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FeatureFlagService featureFlagService;
    private final RoleCapabilityRegistry roleCapabilityRegistry;
    private final AuditLogService auditLogService;
    private final long refreshTokenExpirationDays;

    public DevAuthServiceImpl(
        DevRoleSwitcherAccess devRoleSwitcherAccess,
        UserRepository userRepository,
        RoleRepository roleRepository,
        StoreRepository storeRepository,
        TokenService tokenService,
        RefreshTokenRepository refreshTokenRepository,
        FeatureFlagService featureFlagService,
        RoleCapabilityRegistry roleCapabilityRegistry,
        AuditLogService auditLogService,
        @Value("${app.auth.refresh-token-expiration-days:14}") long refreshTokenExpirationDays
    ) {
        this.devRoleSwitcherAccess = devRoleSwitcherAccess;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.storeRepository = storeRepository;
        this.tokenService = tokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.featureFlagService = featureFlagService;
        this.roleCapabilityRegistry = roleCapabilityRegistry;
        this.auditLogService = auditLogService;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DevTestUserResponse> testUsers() {
        devRoleSwitcherAccess.requireEnabled();
        return DevTestUser.USERS.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public LoginResponse switchUser(String loginIdentifier, HttpServletRequest request) {
        devRoleSwitcherAccess.requireEnabled();
        String normalized = loginIdentifier == null ? "" : loginIdentifier.trim();
        if (!DevTestUser.isAllowed(normalized)) {
            throw new ForbiddenException("Dev role switcher can only switch to predefined dev users");
        }
        User user = userRepository.findFirstByUsernameIgnoreCase(normalized)
            .orElseThrow(() -> new ForbiddenException("Dev test user is not seeded: " + normalized));
        if (user.getStatus() != null && !"active".equalsIgnoreCase(user.getStatus())) {
            throw new ForbiddenException("Dev test user is inactive: " + normalized);
        }
        Role role = roleRepository.findById(user.getRole_id())
            .orElseThrow(() -> new ForbiddenException("Role not found for dev test user: " + normalized));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
            user.getId(),
            user.getStore_id(),
            user.getRole_id(),
            user.getUsername(),
            user.getFull_name(),
            role.getCode() == null ? null : role.getCode().toUpperCase()
        );
        String refreshToken = createRefreshToken(authenticatedUser, request);
        auditLogService.record(
            authenticatedUser.storeId(),
            authenticatedUser,
            "DEV_SWITCH_USER",
            "USER",
            authenticatedUser.userId(),
            "Dev role switcher selected " + authenticatedUser.username(),
            Map.of("role", authenticatedUser.roleCode()),
            request
        );
        return buildLoginResponse(authenticatedUser, refreshToken);
    }

    private DevTestUserResponse toResponse(DevTestUser user) {
        DevTestUserResponse response = new DevTestUserResponse();
        response.loginIdentifier = user.loginIdentifier();
        response.label = user.label();
        response.fullName = user.fullName();
        response.roleCode = user.roleCode();
        return response;
    }

    private LoginResponse buildLoginResponse(AuthenticatedUser user, String refreshToken) {
        Store store = user.storeId() == null
            ? null
            : storeRepository.findById(user.storeId()).orElse(null);
        Long organizationId = store == null ? null : store.organization_id;
        AuthUserResponse userResponse = new AuthUserResponse();
        userResponse.id = user.userId();
        userResponse.username = user.username();
        userResponse.fullName = user.fullName();
        userResponse.roleCode = user.roleCode();
        userResponse.storeId = user.storeId();
        userResponse.organizationId = organizationId;

        LoginResponse response = new LoginResponse();
        response.accessToken = tokenService.createAccessToken(user, organizationId);
        response.refreshToken = refreshToken;
        response.expiresIn = tokenService.getAccessTokenExpirationSeconds();
        response.user = userResponse;
        response.features = featureMap();
        response.permissions = permissionCodes(user.roleCode());
        return response;
    }

    private String createRefreshToken(AuthenticatedUser user, HttpServletRequest request) {
        String refreshToken = tokenService.generateRefreshToken();
        RefreshToken storedToken = new RefreshToken();
        storedToken.userId = user.userId();
        storedToken.storeId = user.storeId();
        storedToken.tokenHash = tokenService.hashRefreshToken(refreshToken);
        storedToken.expiresAt = LocalDateTime.now().plusDays(refreshTokenExpirationDays);
        storedToken.createdAt = LocalDateTime.now();
        storedToken.createdByIp = clientIp(request);
        storedToken.userAgent = request == null ? null : request.getHeader("User-Agent");
        refreshTokenRepository.save(storedToken);
        return refreshToken;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Map<String, Boolean> featureMap() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("core_pos", featureFlagService.isEnabled(FeaturePackage.CORE_POS));
        features.put("printing", featureFlagService.isEnabled(FeaturePackage.PRINTING));
        features.put("kds", featureFlagService.isEnabled(FeaturePackage.KDS));
        features.put("admin", featureFlagService.isEnabled(FeaturePackage.ADMIN));
        features.put("analytics", featureFlagService.isEnabled(FeaturePackage.ANALYTICS));
        features.put("platform", featureFlagService.isEnabled(FeaturePackage.PLATFORM));
        features.put("developer_tools", featureFlagService.isEnabled(FeaturePackage.DEVELOPER_TOOLS));
        return features;
    }

    private List<String> permissionCodes(String roleCode) {
        return roleCapabilityRegistry.getCapabilities(roleCode).stream()
            .map(Capability::name)
            .sorted(Comparator.naturalOrder())
            .toList();
    }
}
