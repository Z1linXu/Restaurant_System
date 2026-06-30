package com.restaurant.system.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank
    @JsonAlias({"loginId", "loginIdentifier", "username"})
    @JsonProperty("login_identifier")
    public String loginIdentifier;

    @NotBlank
    public String password;
}
