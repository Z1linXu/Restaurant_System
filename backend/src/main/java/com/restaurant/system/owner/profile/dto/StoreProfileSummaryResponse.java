package com.restaurant.system.owner.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record StoreProfileSummaryResponse(
    @JsonProperty("profile_code") String profileCode,
    @JsonProperty("display_name") String displayName,
    String description,
    String status,
    String provenance,
    List<StoreProfileVersionSummaryResponse> versions
) {

    public StoreProfileSummaryResponse {
        versions = versions == null ? List.of() : List.copyOf(versions);
    }
}
