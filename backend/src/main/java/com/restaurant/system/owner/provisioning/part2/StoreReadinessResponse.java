package com.restaurant.system.owner.provisioning.part2;

import java.time.LocalDateTime;
import java.util.List;

public class StoreReadinessResponse {

    public Long evidence_id;
    public Long organization_id;
    public Long store_id;
    public String readiness_status;
    public Boolean ready;
    public String store_status;
    public String lifecycle_status;
    public String readiness_fingerprint;
    public LocalDateTime checked_at;
    public LocalDateTime expires_at;
    public List<Check> checks;
    public Counts counts;

    public static class Check {
        public String code;
        public String status;
        public String message;

        public Check() {
        }

        public Check(String code, String status, String message) {
            this.code = code;
            this.status = status;
            this.message = message;
        }
    }

    public static class Counts {
        public int station_count;
        public int table_count;
        public int staff_count;
        public int printer_role_count;
        public int device_count;
    }
}
