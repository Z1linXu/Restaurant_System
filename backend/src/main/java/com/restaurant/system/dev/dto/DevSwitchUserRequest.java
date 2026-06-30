package com.restaurant.system.dev.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class DevSwitchUserRequest {

    @NotBlank
    @JsonAlias({"loginIdentifier", "username"})
    @JsonProperty("login_identifier")
    public String loginIdentifier;
}
