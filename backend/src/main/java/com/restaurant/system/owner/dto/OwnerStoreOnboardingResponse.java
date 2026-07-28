package com.restaurant.system.owner.dto;

import java.util.List;

public class OwnerStoreOnboardingResponse {

    public Long onboarding_request_id;
    public Long organization_id;
    public Long source_store_id;
    public Long store_id;
    public String store_name;
    public String store_code;
    public String store_status;
    public String onboarding_status;
    public String result_code;
    public boolean replayed;
    public List<OwnerStoreOnboardingStaffResponse> staff;
}
