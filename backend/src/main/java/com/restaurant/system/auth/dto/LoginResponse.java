package com.restaurant.system.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class LoginResponse {

    @JsonProperty("access_token")
    public String accessToken;

    @JsonProperty("refresh_token")
    public String refreshToken;

    @JsonProperty("expires_in")
    public long expiresIn;

    public AuthUserResponse user;

    public Map<String, Boolean> features;

    public List<String> permissions;
}
