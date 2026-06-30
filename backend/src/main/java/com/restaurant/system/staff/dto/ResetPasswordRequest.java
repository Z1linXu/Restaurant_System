package com.restaurant.system.staff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class ResetPasswordRequest {

    @NotBlank
    @JsonProperty("new_password")
    public String newPassword;
}
