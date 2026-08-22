package com.restaurant.system.staging.cleanup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StoreFixtureCleanupResponse {

    public Long organization_id;
    public boolean dry_run;
    public String status;
    public List<Long> requested_store_ids = new ArrayList<>();
    public List<StoreResult> stores = new ArrayList<>();
    public Map<String, Integer> deleted_counts = new LinkedHashMap<>();
    public Map<String, Integer> preserved_evidence_counts = new LinkedHashMap<>();
    public List<String> dependency_checks = new ArrayList<>();

    public static class StoreResult {
        public Long store_id;
        public String code;
        public String classification;
        public String disposition;
        public String reason;
    }
}
