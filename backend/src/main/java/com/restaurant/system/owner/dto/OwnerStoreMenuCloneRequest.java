package com.restaurant.system.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OwnerStoreMenuCloneRequest {

    @NotNull
    public Long source_store_id;

    @NotBlank
    public String profile_code;

    @Override
    public String toString() {
        return "OwnerStoreMenuCloneRequest[redacted]";
    }
}
