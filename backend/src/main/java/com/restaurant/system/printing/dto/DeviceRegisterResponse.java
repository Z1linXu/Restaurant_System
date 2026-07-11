package com.restaurant.system.printing.dto;

import com.restaurant.system.printing.entity.StoreDevice;
import java.time.LocalDateTime;

public class DeviceRegisterResponse {
    public Long device_id;
    public Long store_id;
    public String device_name;
    public String device_type;
    public String device_token;
    public String status;
    public LocalDateTime created_at;

    public static DeviceRegisterResponse from(StoreDevice device, String rawToken) {
        DeviceRegisterResponse response = new DeviceRegisterResponse();
        response.device_id = device.id;
        response.store_id = device.storeId;
        response.device_name = device.deviceName;
        response.device_type = device.deviceType;
        response.device_token = rawToken;
        response.status = device.status;
        response.created_at = device.createdAt;
        return response;
    }
}
