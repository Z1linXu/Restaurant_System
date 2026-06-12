package com.restaurant.system.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthUserResponse {

    public Long id;

    public String username;

    @JsonProperty("full_name")
    public String fullName;

    @JsonProperty("role_code")
    public String roleCode;

    @JsonProperty("store_id")
    public Long storeId;

    @JsonProperty("organization_id")
    public Long organizationId;
}
