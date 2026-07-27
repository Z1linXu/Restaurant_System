package com.restaurant.system.owner.dto;

import jakarta.validation.constraints.NotBlank;

public class OwnerStoreOnboardingStaffRequest {

    @NotBlank
    public String login_identifier;

    public String full_name;

    @NotBlank
    public String role_code;

    @NotBlank
    public String initial_password;

    @Override
    public String toString() {
        return "OwnerStoreOnboardingStaffRequest[redacted]";
    }
}
