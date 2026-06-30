package com.restaurant.system.staff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.system.user.entity.Store;

public class StaffStoreResponse {

    public Long id;

    @JsonProperty("organization_id")
    public Long organizationId;

    public String name;

    public String code;

    public String status;

    public static StaffStoreResponse from(Store store) {
        StaffStoreResponse response = new StaffStoreResponse();
        response.id = store.id;
        response.organizationId = store.organization_id;
        response.name = store.name;
        response.code = store.code;
        response.status = store.status;
        return response;
    }
}
