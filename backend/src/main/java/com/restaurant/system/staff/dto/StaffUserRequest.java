package com.restaurant.system.staff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class StaffUserRequest {

    @NotNull
    @JsonProperty("store_id")
    public Long storeId;

    @NotBlank
    public String username;

    @JsonProperty("full_name")
    public String fullName;

    public String phone;

    @NotBlank
    @JsonProperty("role_code")
    public String roleCode;

    public String password;
}
