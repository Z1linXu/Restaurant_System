package com.restaurant.system.owner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class OwnerStoreOnboardingRequest {

    @NotNull
    public Long source_store_id;

    @NotBlank
    public String store_name;

    @NotBlank
    public String store_code;

    @NotEmpty
    @Valid
    public List<OwnerStoreOnboardingStaffRequest> staff;

    @Override
    public String toString() {
        return "OwnerStoreOnboardingRequest[redacted]";
    }
}
