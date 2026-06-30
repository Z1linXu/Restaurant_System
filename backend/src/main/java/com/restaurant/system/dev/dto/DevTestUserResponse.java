package com.restaurant.system.dev.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DevTestUserResponse {

    @JsonProperty("login_identifier")
    public String loginIdentifier;

    public String label;

    @JsonProperty("full_name")
    public String fullName;

    @JsonProperty("role_code")
    public String roleCode;
}
