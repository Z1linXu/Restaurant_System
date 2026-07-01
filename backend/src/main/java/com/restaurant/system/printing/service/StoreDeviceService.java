package com.restaurant.system.printing.service;

import com.restaurant.system.printing.dto.DeviceHeartbeatRequest;
import com.restaurant.system.printing.dto.DeviceRegisterRequest;
import com.restaurant.system.printing.dto.DeviceRegisterResponse;
import com.restaurant.system.printing.dto.StoreDeviceResponse;
import com.restaurant.system.printing.entity.StoreDevice;
import java.util.List;

public interface StoreDeviceService {

    DeviceRegisterResponse registerDevice(DeviceRegisterRequest request);

    StoreDevice authenticateDevice(Long deviceId, String rawDeviceToken);

    StoreDeviceResponse heartbeat(Long deviceId, String rawDeviceToken, DeviceHeartbeatRequest request);

    List<StoreDeviceResponse> listStoreDevices(Long storeId);
}
