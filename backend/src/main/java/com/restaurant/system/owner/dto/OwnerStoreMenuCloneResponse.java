package com.restaurant.system.owner.dto;

import java.util.List;

public class OwnerStoreMenuCloneResponse {

    public Long clone_request_id;
    public Long organization_id;
    public Long source_store_id;
    public Long target_store_id;
    public String profile_code;
    public Long source_menu_revision;
    public Long target_revision_before;
    public Long target_revision_after;
    public String status;
    public boolean replayed;
    public OwnerStoreMenuCloneCreatedCounts created;
    public String result_code;
    public List<String> warnings;
}
