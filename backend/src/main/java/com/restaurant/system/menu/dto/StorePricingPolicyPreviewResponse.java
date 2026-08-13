package com.restaurant.system.menu.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class StorePricingPolicyPreviewResponse {

    public Long store_id;
    public StorePricingPolicyResponse current_policy;
    public StorePricingPolicyResponse proposed_policy;
    public List<ImpactGroup> impact_groups = new ArrayList<>();

    public static class ImpactGroup {
        public String policy_key;
        public BigDecimal old_delta;
        public BigDecimal new_delta;
        public Integer affected_item_count;
        public List<ImpactItem> sample_items = new ArrayList<>();
    }

    public static class ImpactItem {
        public Long item_id;
        public String sku;
        public String name_zh;
        public String name_en;
        public BigDecimal old_price;
        public BigDecimal new_price;
    }
}
