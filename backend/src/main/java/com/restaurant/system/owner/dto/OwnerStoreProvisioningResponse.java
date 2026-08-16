package com.restaurant.system.owner.dto;

import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningResult;

public class OwnerStoreProvisioningResponse {

    public Long request_id;
    public Long store_id;
    public String status;
    public Boolean replayed;
    public String validation_status;
    public String result_code;
    public String error_code;
    public CountsResponse counts;

    public static OwnerStoreProvisioningResponse from(OwnerStoreProvisioningResult result) {
        OwnerStoreProvisioningResponse response = new OwnerStoreProvisioningResponse();
        response.request_id = result.requestId();
        response.store_id = result.storeId();
        response.status = result.status();
        response.replayed = result.replayed();
        response.validation_status = result.validationStatus();
        response.result_code = result.resultCode();
        response.error_code = result.errorCode();
        response.counts = CountsResponse.from(result.counts());
        return response;
    }

    public static class CountsResponse {
        public int station_count;
        public int category_count;
        public int item_count;
        public int option_count;
        public int pricing_policy_count;
        public int combo_component_count;
        public int printing_rule_count;

        private static CountsResponse from(com.restaurant.system.owner.provisioning.OwnerStoreProvisioningCounts counts) {
            CountsResponse response = new CountsResponse();
            response.station_count = counts.stationCount();
            response.category_count = counts.categoryCount();
            response.item_count = counts.itemCount();
            response.option_count = counts.optionCount();
            response.pricing_policy_count = counts.pricingPolicyCount();
            response.combo_component_count = counts.comboComponentCount();
            response.printing_rule_count = counts.printingRuleCount();
            return response;
        }
    }
}
