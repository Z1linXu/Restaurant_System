package com.restaurant.system.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.auth.service.TokenService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.RequestUserContextService;
import com.restaurant.system.common.auth.UnauthorizedException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    public AuthTokenFilter(
        TokenService tokenService,
        UserRepository userRepository,
        RoleRepository roleRepository,
        ObjectMapper objectMapper
    ) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            TokenService.AccessTokenClaims claims = tokenService.parseAccessToken(authorization.substring("Bearer ".length()).trim());
            User user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new UnauthorizedException("User not found for bearer token"));
            if (user.getStatus() != null && !"active".equalsIgnoreCase(user.getStatus())) {
                throw new UnauthorizedException("User is not active");
            }
            Role role = roleRepository.findById(user.getRole_id())
                .orElseThrow(() -> new UnauthorizedException("Role not found for bearer token"));
            AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                user.getStore_id(),
                user.getRole_id(),
                user.getUsername(),
                user.getFull_name(),
                role.getCode() == null ? claims.roleCode() : role.getCode().toUpperCase()
            );
            request.setAttribute(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, authenticatedUser);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException exception) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(exception.getMessage())));
        }
    }
}
