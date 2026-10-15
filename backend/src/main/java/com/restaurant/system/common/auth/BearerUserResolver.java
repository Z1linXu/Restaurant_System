package com.restaurant.system.common.auth;

import com.restaurant.system.auth.service.TokenService;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.UserRepository;
import org.springframework.stereotype.Service;

/** One Bearer identity rule shared by HTTP and continuously authorized STOMP. */
@Service
public class BearerUserResolver {
    private final TokenService tokens;
    private final UserRepository users;
    private final RoleRepository roles;
    public BearerUserResolver(TokenService tokens, UserRepository users, RoleRepository roles) {
        this.tokens=tokens; this.users=users; this.roles=roles;
    }
    public AuthenticatedUser resolve(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new UnauthorizedException("Bearer token is required");
        var claims=tokens.parseAccessToken(authorization.substring(7).trim());
        if (claims.userId() == null) throw new UnauthorizedException("Invalid bearer identity");
        var user=users.findById(claims.userId()).orElseThrow(() -> new UnauthorizedException("Bearer identity unavailable"));
        if (!"active".equalsIgnoreCase(user.getStatus()) || user.getRole_id() == null) throw new UnauthorizedException("Bearer identity unavailable");
        var role=roles.findById(user.getRole_id()).orElseThrow(() -> new UnauthorizedException("Bearer role unavailable"));
        if (role.getCode() == null || role.getCode().isBlank()) throw new UnauthorizedException("Bearer role unavailable");
        return new AuthenticatedUser(user.getId(),user.getStore_id(),user.getRole_id(),user.getUsername(),user.getFull_name(),role.getCode().toUpperCase());
    }
}
