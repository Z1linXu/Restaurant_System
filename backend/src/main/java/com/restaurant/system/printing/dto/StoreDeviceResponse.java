package com.restaurant.system.printing.dto;

import com.restaurant.system.printing.entity.StoreDevice;
import java.time.LocalDateTime;

public class StoreDeviceResponse {
    public Long id;
    public Long organization_id;
    public Long store_id;
    public String device_name;
    public String device_type;
    public String status;
    public LocalDateTime last_seen_at;
    public String app_version;
    public String platform;
    public Boolean is_active;
    public LocalDateTime created_at;
    public LocalDateTime updated_at;

    public static StoreDeviceResponse from(StoreDevice device) {
        StoreDeviceResponse response = new StoreDeviceResponse();
        response.id = device.id;
        response.organization_id = device.organizationId;
        response.store_id = device.storeId;
        response.device_name = device.deviceName;
        response.device_type = device.deviceType;
        response.status = device.status;
        response.last_seen_at = device.lastSeenAt;
        response.app_version = device.appVersion;
        response.platform = device.platform;
        response.is_active = device.isActive;
        response.created_at = device.createdAt;
        response.updated_at = device.updatedAt;
        return response;
    }
}
