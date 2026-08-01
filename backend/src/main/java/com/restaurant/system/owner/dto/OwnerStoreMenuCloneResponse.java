package com.restaurant.system.owner.dto;

import java.util.List;
import java.util.Map;

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
    public Map<String, Long> category_ids_by_code;
    public Map<String, Long> station_ids_by_code;
    public Map<String, Long> item_ids_by_sku;
    public List<String> warnings;
}
