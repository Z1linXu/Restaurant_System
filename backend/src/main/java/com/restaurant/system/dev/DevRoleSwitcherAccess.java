package com.restaurant.system.dev;

import com.restaurant.system.common.auth.ForbiddenException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DevRoleSwitcherAccess {

    private final Environment environment;
    private final boolean enabled;

    public DevRoleSwitcherAccess(
        Environment environment,
        @Value("${app.dev-tools.role-switcher-enabled:false}") boolean enabled
    ) {
        this.environment = environment;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled && hasLocalOrDevProfile();
    }

    public void requireEnabled() {
        if (!isEnabled()) {
            throw new ForbiddenException("Dev role switcher is disabled");
        }
    }

    private boolean hasLocalOrDevProfile() {
        return Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> "local".equalsIgnoreCase(profile) || "dev".equalsIgnoreCase(profile));
    }
}
