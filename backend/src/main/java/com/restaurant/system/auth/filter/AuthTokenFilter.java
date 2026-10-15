package com.restaurant.system.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.BearerUserResolver;
import com.restaurant.system.common.auth.RequestUserContextService;
import com.restaurant.system.common.auth.UnauthorizedException;
import com.restaurant.system.common.response.ApiResponse;
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

    private final BearerUserResolver identities;
    private final ObjectMapper objectMapper;

    public AuthTokenFilter(BearerUserResolver identities, ObjectMapper objectMapper) {
        this.identities = identities;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
            && "/api/v1/integrations/uber-eats/webhook".equals(request.getServletPath());
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
            AuthenticatedUser authenticatedUser = identities.resolve(authorization);
            request.setAttribute(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, authenticatedUser);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException exception) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(exception.getMessage())));
        }
    }
}
