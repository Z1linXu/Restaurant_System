package com.restaurant.system.owner.provisioning.part2;

import java.time.LocalDateTime;
import java.util.List;

public class StorePart2ProvisioningResponse {

    public Long request_id;
    public Long store_id;
    public String status;
    public String readiness_status;
    public Boolean replayed;
    public String result_code;
    public String error_code;
    public Counts counts;
    public List<StaffResult> staff;
    public List<StaffCredential> synthetic_staff_credentials;
    public List<DeviceCredential> synthetic_device_credentials;
    public StoreReadinessResponse readiness;

    public static class Counts {
        public int station_count;
        public int table_count;
        public int staff_count;
        public int printer_role_count;
        public int device_count;
    }

    public static class StaffResult {
        public Long user_id;
        public String login_identifier;
        public String role_code;
    }

    /** Runtime-only delivery for a synthetic Staging identity. */
    public static class StaffCredential {
        public String login_identifier;
        public String temporary_password;
        public String role_code;
        public Boolean one_time = true;
    }

    /** Runtime-only delivery; never persisted into evidence or logs. */
    public static class DeviceCredential {
        public Long device_id;
        public String device_name;
        public String device_token;
        public Boolean one_time = true;
    }
}
