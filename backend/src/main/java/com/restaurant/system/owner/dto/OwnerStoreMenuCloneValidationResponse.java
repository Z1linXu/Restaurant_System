package com.restaurant.system.owner.dto;

import java.util.List;

/** Safe public result of the read-only clone planner. */
public class OwnerStoreMenuCloneValidationResponse {

    public boolean valid;
    public String profile_code;
    public Long source_menu_revision;
    public Long target_menu_revision;
    public OwnerStoreMenuCloneCreatedCounts expected;
    public List<String> missing_codes;
    public List<String> duplicate_codes;
    public List<String> warnings;
}
