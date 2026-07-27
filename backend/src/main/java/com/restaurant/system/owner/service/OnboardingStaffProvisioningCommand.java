package com.restaurant.system.owner.service;

/**
 * Internal onboarding command. The password is deliberately excluded from
 * {@link #toString()} so accidental diagnostic logging cannot expose it.
 */
public final class OnboardingStaffProvisioningCommand {

    private final Long organizationId;
    private final Long storeId;
    private final String roleCode;
    private final String loginIdentifier;
    private final String fullName;
    private final String rawPassword;

    public OnboardingStaffProvisioningCommand(
        Long organizationId,
        Long storeId,
        String roleCode,
        String loginIdentifier,
        String fullName,
        String rawPassword
    ) {
        this.organizationId = organizationId;
        this.storeId = storeId;
        this.roleCode = roleCode;
        this.loginIdentifier = loginIdentifier;
        this.fullName = fullName;
        this.rawPassword = rawPassword;
    }

    public Long organizationId() {
        return organizationId;
    }

    public Long storeId() {
        return storeId;
    }

    public String roleCode() {
        return roleCode;
    }

    public String loginIdentifier() {
        return loginIdentifier;
    }

    public String fullName() {
        return fullName;
    }

    public String rawPassword() {
        return rawPassword;
    }

    @Override
    public String toString() {
        return "OnboardingStaffProvisioningCommand[redacted]";
    }
}
