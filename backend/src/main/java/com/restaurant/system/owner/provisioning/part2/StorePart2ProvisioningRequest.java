package com.restaurant.system.owner.provisioning.part2;

import java.util.ArrayList;
import java.util.List;

/**
 * Store-local desired state for the synthetic Part 2 workflow.
 * Passwords are accepted only at the runtime boundary and are never copied
 * into the request ledger or readiness evidence.
 */
public class StorePart2ProvisioningRequest {

    public List<StationSpec> stations = new ArrayList<>();
    public List<TableSpec> tables = new ArrayList<>();
    public List<StaffSpec> staff = new ArrayList<>();
    public List<PrinterRoleSpec> printer_roles = new ArrayList<>();
    public List<DeviceSpec> devices = new ArrayList<>();

    public static class StationSpec {
        public String code;
        public String name;
        public String name_zh;
        public String name_en;
        public String station_type;
        public Integer sort_order;
        public Boolean is_active;
    }

    public static class TableSpec {
        public String table_code;
        public String table_name;
        public String area_name;
        public String table_config;
        public Integer capacity;
        public Boolean supports_split;
        public Integer sort_order;
        public Boolean is_active;
    }

    public static class StaffSpec {
        public String role_code;
        public String login_identifier;
        public String full_name;
        public String temporary_password;
    }

    public static class PrinterRoleSpec {
        public String role_code;
        public String module_code;
        public String display_name;
        public String mode;
        public Boolean enabled;
        public Boolean required;
    }

    public static class DeviceSpec {
        public String device_name;
        public String device_type;
        public String app_version;
        public String platform;
        public Boolean trusted_build;
        public String worker_status;
    }
}
