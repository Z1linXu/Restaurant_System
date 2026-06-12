package com.restaurant.system.common.auth;

import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RequestUserContextService {

    public static final String AUTHENTICATED_USER_ATTRIBUTE = RequestUserContextService.class.getName() + ".currentUser";
    static final String USER_ID_HEADER = "X-User-Id";

    private final HttpServletRequest request;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final boolean xUserIdFallbackEnabled;

    public RequestUserContextService(
        HttpServletRequest request,
        UserRepository userRepository,
        RoleRepository roleRepository,
        @Value("${app.auth.x-user-id-fallback-enabled:true}") boolean xUserIdFallbackEnabled
    ) {
        this.request = request;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.xUserIdFallbackEnabled = xUserIdFallbackEnabled;
    }

    public AuthenticatedUser getRequiredUser() {
        Object cached = request.getAttribute(AUTHENTICATED_USER_ATTRIBUTE);
        if (cached instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }

        String rawUserId = request.getHeader(USER_ID_HEADER);
        if (!xUserIdFallbackEnabled && rawUserId != null && !rawUserId.isBlank()) {
            throw new UnauthorizedException("X-User-Id fallback is disabled");
        }
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new UnauthorizedException("Authorization Bearer token is required");
        }

        Long userId;
        try {
            userId = Long.valueOf(rawUserId);
        } catch (NumberFormatException ex) {
            throw new UnauthorizedException(USER_ID_HEADER + " must be a numeric user id");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found for id " + userId));

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

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
            user.getId(),
            user.getStore_id(),
            user.getRole_id(),
            user.getUsername(),
            user.getFull_name(),
            role.getCode().toUpperCase()
        );
        request.setAttribute(AUTHENTICATED_USER_ATTRIBUTE, authenticatedUser);
        return authenticatedUser;
    }
}
