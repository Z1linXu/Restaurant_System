package com.restaurant.system.owner.provisioning.part2;

public class StoreActivationResponse {

    public Long request_id;
    public Long organization_id;
    public Long store_id;
    public String status;
    public String target_state;
    public Boolean replayed;
    public String result_code;
    public String error_code;
    public StoreReadinessResponse readiness;
}
