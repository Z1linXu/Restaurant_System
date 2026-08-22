package com.restaurant.system.owner.provisioning.part2;

import java.time.LocalDateTime;

public class DeviceReadinessProofResponse {

    public Long device_id;
    public Long store_id;
    public String proof_status;
    public String worker_status;
    public Boolean trusted_build;
    public LocalDateTime last_heartbeat_at;
    public LocalDateTime expires_at;
}
