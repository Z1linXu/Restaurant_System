package com.restaurant.system.owner.dto;

import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningCounts;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningResult;
import com.restaurant.system.user.StoreOperationalState;
import com.restaurant.system.user.entity.Store;

public class OwnerBusinessStoreCreateResponse {

    public Long request_id;
    public Long store_id;
    public String store_name;
    public String store_code;
    public String store_kind;
    public String store_status;
    public String lifecycle_status;
    public String operational_state;
    public Boolean is_live;
    public String status;
    public Boolean replayed;
    public String validation_status;
    public String result_code;
    public OwnerStoreProvisioningResponse.CountsResponse counts;

    public static OwnerBusinessStoreCreateResponse from(
        OwnerStoreProvisioningResult result,
        Store store
    ) {
        OwnerBusinessStoreCreateResponse response = new OwnerBusinessStoreCreateResponse();
        response.request_id = result.requestId();
        response.store_id = store.id;
        response.store_name = store.name;
        response.store_code = store.code;
        response.store_kind = store.store_kind;
        response.store_status = store.status;
        response.lifecycle_status = store.lifecycle_status;
        response.operational_state = StoreOperationalState.value(store);
        response.is_live = StoreOperationalState.isLive(store);
        response.status = result.status();
        response.replayed = result.replayed();
        response.validation_status = result.validationStatus();
        response.result_code = result.resultCode();
        response.counts = counts(result.counts());
        return response;
    }

    private static OwnerStoreProvisioningResponse.CountsResponse counts(OwnerStoreProvisioningCounts counts) {
        OwnerStoreProvisioningResponse.CountsResponse response = new OwnerStoreProvisioningResponse.CountsResponse();
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
