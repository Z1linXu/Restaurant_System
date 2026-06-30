package com.restaurant.system.owner.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class OwnerOverviewResponse {

    public List<OrganizationOverview> organizations;
    public LocalDateTime generated_at;

    public static class OrganizationOverview {
        public Long id;
        public String name;
        public String code;
        public String status;
        public String role_code;
        public List<StoreOverview> stores;
    }

    public static class StoreOverview {
        public Long id;
        public String name;
        public String code;
        public String status;
        public String role_code;
        public Map<String, Boolean> features;
        public StoreSummary summary;
    }

    public static class StoreSummary {
        public Long today_orders;
        public BigDecimal today_sales;
        public Long active_orders;
        public Long occupied_tables;
        public Long open_tables;
        public Long failed_print_jobs;
        public String printing_mode;
        public LocalDateTime last_failed_print_at;
        public Long kds_active_count;
        public LocalDateTime last_updated_at;
    }
}
