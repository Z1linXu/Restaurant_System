package com.restaurant.system.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank
    @JsonProperty("login_identifier")
    public String loginIdentifier;

    @NotBlank
    public String password;
}
