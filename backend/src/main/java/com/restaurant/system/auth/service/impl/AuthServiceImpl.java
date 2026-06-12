package com.restaurant.system.auth.service.impl;

import com.restaurant.system.auth.dto.AuthUserResponse;
import com.restaurant.system.auth.dto.LoginRequest;
import com.restaurant.system.auth.dto.LoginResponse;
import com.restaurant.system.auth.entity.RefreshToken;
import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.RefreshTokenRepository;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.AuthService;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.auth.service.TokenService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.RoleCapabilityRegistry;
import com.restaurant.system.common.auth.UnauthorizedException;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
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
public class AuthServiceImpl implements AuthService {

    private static final String PASSWORD_ALGORITHM_BCRYPT = "BCRYPT";

    private final UserCredentialRepository userCredentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StoreRepository storeRepository;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final FeatureFlagService featureFlagService;
    private final RoleCapabilityRegistry roleCapabilityRegistry;
    private final long refreshTokenExpirationDays;

    public AuthServiceImpl(
        UserCredentialRepository userCredentialRepository,
        RefreshTokenRepository refreshTokenRepository,
        UserRepository userRepository,
        RoleRepository roleRepository,
        StoreRepository storeRepository,
        PasswordService passwordService,
        TokenService tokenService,
        FeatureFlagService featureFlagService,
        RoleCapabilityRegistry roleCapabilityRegistry,
        @Value("${app.auth.refresh-token-expiration-days:14}") long refreshTokenExpirationDays
    ) {
        this.userCredentialRepository = userCredentialRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.storeRepository = storeRepository;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.featureFlagService = featureFlagService;
        this.roleCapabilityRegistry = roleCapabilityRegistry;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        UserCredential credential = userCredentialRepository
            .findFirstByLoginIdentifierIgnoreCaseAndIsActiveTrue(request.loginIdentifier)
            .orElseThrow(() -> new UnauthorizedException("Invalid login identifier or password"));

        if (!PASSWORD_ALGORITHM_BCRYPT.equalsIgnoreCase(credential.passwordAlgorithm)
            || !passwordService.matches(request.password, credential.passwordHash)) {
            throw new UnauthorizedException("Invalid login identifier or password");
        }

        AuthenticatedUser authenticatedUser = buildAuthenticatedUser(credential.userId);
        String refreshToken = createRefreshToken(authenticatedUser, servletRequest);
        return buildLoginResponse(authenticatedUser, refreshToken);
    }

    @Override
    @Transactional
    public LoginResponse refresh(String refreshToken, HttpServletRequest servletRequest) {
        RefreshToken storedToken = refreshTokenRepository.findFirstByTokenHash(tokenService.hashRefreshToken(refreshToken))
            .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));
        if (storedToken.revokedAt != null) {
            throw new UnauthorizedException("Refresh token revoked");
        }
        if (storedToken.expiresAt == null || !storedToken.expiresAt.isAfter(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token expired");
        }

        AuthenticatedUser authenticatedUser = buildAuthenticatedUser(storedToken.userId);
        storedToken.revokedAt = LocalDateTime.now();
        refreshTokenRepository.save(storedToken);
        String rotatedRefreshToken = createRefreshToken(authenticatedUser, servletRequest);
        return buildLoginResponse(authenticatedUser, rotatedRefreshToken);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = tokenService.hashRefreshToken(refreshToken);
        refreshTokenRepository.findFirstByTokenHash(tokenHash).ifPresent(storedToken -> {
            if (storedToken.revokedAt == null) {
                storedToken.revokedAt = LocalDateTime.now();
                refreshTokenRepository.save(storedToken);
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse me(AuthenticatedUser currentUser) {
        return buildLoginResponse(currentUser, null);
    }

    private AuthenticatedUser buildAuthenticatedUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (user.getStatus() != null && !"active".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("User is not active");
        }
        if (user.getRole_id() == null) {
            throw new ForbiddenException("User role is not configured");
        }
        Role role = roleRepository.findById(user.getRole_id())
            .orElseThrow(() -> new ForbiddenException("Role not found for user " + userId));
        if (role.getCode() == null || role.getCode().isBlank()) {
            throw new ForbiddenException("Role code is not configured");
        }
        return new AuthenticatedUser(
            user.getId(),
            user.getStore_id(),
            user.getRole_id(),
            user.getUsername(),
            user.getFull_name(),
            role.getCode().toUpperCase()
        );
    }

    private LoginResponse buildLoginResponse(AuthenticatedUser user, String refreshToken) {
        Store store = user.storeId() == null
            ? null
            : storeRepository.findById(user.storeId()).orElse(null);
        Long organizationId = store == null ? null : store.organization_id;
        String accessToken = tokenService.createAccessToken(user, organizationId);
        AuthUserResponse userResponse = new AuthUserResponse();
        userResponse.id = user.userId();
        userResponse.username = user.username();
        userResponse.fullName = user.fullName();
        userResponse.roleCode = user.roleCode();
        userResponse.storeId = user.storeId();
        userResponse.organizationId = organizationId;

        LoginResponse response = new LoginResponse();
        response.accessToken = accessToken;
        response.refreshToken = refreshToken;
        response.expiresIn = tokenService.getAccessTokenExpirationSeconds();
        response.user = userResponse;
        response.features = featureMap();
        response.permissions = permissionCodes(user.roleCode());
        return response;
    }

    private String createRefreshToken(AuthenticatedUser user, HttpServletRequest servletRequest) {
        String refreshToken = tokenService.generateRefreshToken();
        RefreshToken storedToken = new RefreshToken();
        storedToken.userId = user.userId();
        storedToken.storeId = user.storeId();
        storedToken.tokenHash = tokenService.hashRefreshToken(refreshToken);
        storedToken.expiresAt = LocalDateTime.now().plusDays(refreshTokenExpirationDays);
        storedToken.createdAt = LocalDateTime.now();
        storedToken.createdByIp = clientIp(servletRequest);
        storedToken.userAgent = servletRequest == null ? null : servletRequest.getHeader("User-Agent");
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
