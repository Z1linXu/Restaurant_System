package com.restaurant.system.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class RefreshTokenRequest {

    @NotBlank
    @JsonProperty("refresh_token")
    public String refreshToken;
}
