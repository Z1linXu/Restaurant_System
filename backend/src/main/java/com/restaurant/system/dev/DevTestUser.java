package com.restaurant.system.dev;

import java.util.List;

public record DevTestUser(
    String loginIdentifier,
    String label,
    String fullName,
    String roleCode
) {

    public static final List<DevTestUser> USERS = List.of(
        new DevTestUser("dev_owner", "Owner / Admin", "Dev Owner", "OWNER"),
        new DevTestUser("dev_frontdesk", "Frontdesk", "Dev Frontdesk", "FRONTDESK"),
        new DevTestUser("dev_kitchen", "Kitchen", "Dev Kitchen", "HOT_KITCHEN"),
        new DevTestUser("dev_runner", "Runner", "Dev Runner", "PASS"),
        new DevTestUser("dev_platform_admin", "Platform Admin", "Dev Platform Admin", "ADMIN")
    );

    public static boolean isAllowed(String loginIdentifier) {
        return USERS.stream().anyMatch(user -> user.loginIdentifier().equalsIgnoreCase(loginIdentifier));
    }
}
