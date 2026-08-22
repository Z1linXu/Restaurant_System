package com.restaurant.system.owner.provisioning.part2;

public interface StoreDeviceReadinessProofService {

    DeviceReadinessProofResponse record(Long deviceId, String rawToken, DeviceReadinessProofRequest request);
}
