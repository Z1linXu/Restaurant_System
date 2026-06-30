package com.restaurant.system.staff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.system.user.entity.User;
import java.time.LocalDateTime;

public class StaffUserResponse {

    public Long id;

    @JsonProperty("store_id")
    public Long storeId;

    @JsonProperty("role_id")
    public Long roleId;

    @JsonProperty("role_code")
    public String roleCode;

    public String username;

    @JsonProperty("full_name")
    public String fullName;

    public String phone;

    public String status;

    @JsonProperty("created_at")
    public LocalDateTime createdAt;

    @JsonProperty("updated_at")
    public LocalDateTime updatedAt;

    public static StaffUserResponse from(User user, String roleCode) {
        StaffUserResponse response = new StaffUserResponse();
        response.id = user.getId();
        response.storeId = user.getStore_id();
        response.roleId = user.getRole_id();
        response.roleCode = roleCode;
        response.username = user.getUsername();
        response.fullName = user.getFull_name();
        response.phone = user.getPhone();
        response.status = user.getStatus();
        response.createdAt = user.getCreated_at();
        response.updatedAt = user.getUpdated_at();
        return response;
    }
}
