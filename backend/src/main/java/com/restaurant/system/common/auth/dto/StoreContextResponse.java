package com.restaurant.system.common.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StoreContextResponse {
    public Long id;
    public String name;
    public String code;
    public String status;

    @JsonProperty("organization_id")
    public Long organizationId;

    @JsonProperty("organization_name")
    public String organizationName;

    @JsonProperty("organization_code")
    public String organizationCode;

    @JsonProperty("role_code")
    public String roleCode;
}
